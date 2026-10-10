import { lingui, linguiTransformerBabelPreset } from "@lingui/vite-plugin"
import babel from "@rolldown/plugin-babel"
import react, { reactCompilerPreset } from "@vitejs/plugin-react"
import { defineConfig, type HtmlTagDescriptor, type Plugin } from "vite"
import checker from "vite-plugin-checker"

// The scripts only discover the text font once the first text is drawn, and the translations once they have started.
// Both are needed for the first screen (public pages, often shown in an iframe of another website, included), so the
// page asks for them right away: the font, and the translations for the language of the browser.
function preloadFirstScreenAssets(): Plugin {
    return {
        name: "preload-first-screen-assets",
        apply: "build",
        transformIndexHtml: {
            order: "post",
            handler: (_html, ctx) => {
                const files = Object.values(ctx.bundle ?? {})
                const tags: HtmlTagDescriptor[] = []

                const font = files.find(f => f.type === "asset" && /^assets\/open-sans-latin-400-normal-.*\.woff2$/.test(f.fileName))
                if (font) {
                    tags.push({
                        tag: "link",
                        attrs: { rel: "preload", as: "font", type: "font/woff2", crossorigin: true, href: `./${font.fileName}` },
                        injectTo: "head",
                    })
                }

                // files to load per language: the translations (see i18n.ts) and the dayjs locale
                const locales: Record<string, string[]> = {}
                for (const f of files) {
                    if (f.type !== "chunk" || !f.facadeModuleId) continue
                    const match = /\/(?:locales\/([\w-]+)\/messages\.po|dayjs\/locale\/([\w-]+)\.js)$/.exec(f.facadeModuleId)
                    const locale = match?.[1] ?? match?.[2]
                    if (locale) locales[locale] = [...(locales[locale] ?? []), `./${f.fileName}`]
                }
                if (locales.en) {
                    // same choice of language as useI18n() before the user's settings are known
                    tags.push({
                        tag: "script",
                        children: `(()=>{const files=${JSON.stringify(locales)};const locale=navigator.languages.map(l=>l.split("-")[0]).find(l=>files[l])??"en";for(const href of files[locale]){const link=document.createElement("link");link.rel="modulepreload";link.crossOrigin="";link.href=href;document.head.appendChild(link)}})()`,
                        // before the stylesheets, a script after them waits until they have downloaded
                        injectTo: "head-prepend",
                    })
                }
                return tags
            },
        },
    }
}

export default defineConfig(() => ({
    plugins: [
        preloadFirstScreenAssets(),
        babel({ presets: [linguiTransformerBabelPreset(), reactCompilerPreset()] }),
        react(),
        lingui(),
        checker({
            // temporary disabled until TypeScript 7 exposes a stable api
            // typescript: true,
            biome: {
                command: "check",
                flags: "--error-on-warnings",
            },
        }),
    ],
    base: "./",
    server: {
        port: 8082,
        proxy: {
            "/rest": "http://localhost:8083",
            "/next": "http://localhost:8083",
            "/ws": "ws://localhost:8083",
            "/openapi": "http://localhost:8083",
            "/api-documentation": "http://localhost:8083",
            "/custom_css.css": "http://localhost:8083",
            "/custom_js.js": "http://localhost:8083",
            "/j_security_check": "http://localhost:8083",
            "/logout": "http://localhost:8083",
        },
    },
    resolve: {
        tsconfigPaths: true,
    },
    legacy: {
        // required for websocket-heartbeat-js
        inconsistentCjsInterop: true,
    },
    test: {
        isolate: false,
        environment: "jsdom",
        globals: true,
        setupFiles: "./src/setupTests.ts",
    },
    build: {
        chunkSizeWarningLimit: 4000,
        rolldownOptions: {
            checks: {
                pluginTimings: false,
            },
            output: {
                codeSplitting: {
                    groups: [
                        // output mantine as its own chunk because it is quite large
                        {
                            name: "mantine",
                            test: "@mantine",
                        },
                    ],
                },
            },
        },
    },
}))
