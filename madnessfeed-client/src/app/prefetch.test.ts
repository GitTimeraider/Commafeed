import { afterEach, describe, expect, it } from "vitest"
import {
    prefetchedOr,
    takePrefetchedApp,
    takePrefetchedAppEntries,
    takePrefetchedPublicEntries,
    takePrefetchedPublicTree,
} from "@/app/prefetch"
import type { Entries, GetEntriesPaginatedRequest, PublicCategory, Settings } from "@/app/types"

const entries = { name: "all" } as Entries
const request: GetEntriesPaginatedRequest = { id: "all", order: "desc", readType: "unread", offset: 0, limit: 50 }

describe("prefetch", () => {
    afterEach(() => {
        window.madnessFeedPublicPagePrefetch = undefined
        window.madnessFeedAppPrefetch = undefined
    })

    it("hands out public page responses once, for the page they were asked for", () => {
        const tree = Promise.resolve({ id: "all" } as PublicCategory)
        const publicEntries = Promise.resolve(entries)
        window.madnessFeedPublicPagePrefetch = { token: "abc", type: "category", id: "all", tree, entries: publicEntries }
        expect(takePrefetchedPublicTree("other")).toBeUndefined()
        expect(takePrefetchedPublicEntries("abc", "feed", "all")).toBeUndefined()
        expect(takePrefetchedPublicTree("abc")).toBe(tree)
        expect(takePrefetchedPublicTree("abc")).toBeUndefined()
        expect(takePrefetchedPublicEntries("abc", "category", "all")).toBe(publicEntries)
        expect(takePrefetchedPublicEntries("abc", "category", "all")).toBeUndefined()
    })

    it("hands out application responses once", () => {
        const settings = Promise.resolve({ language: "en" } as Settings)
        window.madnessFeedAppPrefetch = { settings }
        expect(takePrefetchedApp("settings")).toBe(settings)
        expect(takePrefetchedApp("settings")).toBeUndefined()
        expect(takePrefetchedApp("tree")).toBeUndefined()
    })

    it("uses prefetched entries only for the same request", async () => {
        const prefetched = () => ({ entries: Promise.resolve({ sourceType: "category", request, data: entries }) })

        window.madnessFeedAppPrefetch = prefetched()
        expect(await takePrefetchedAppEntries("category", { ...request, keywords: "" })).toBe(entries)
        expect(takePrefetchedAppEntries("category", request)).toBeUndefined()

        window.madnessFeedAppPrefetch = prefetched()
        await expect(takePrefetchedAppEntries("category", { ...request, readType: "all" })).rejects.toThrow()
        window.madnessFeedAppPrefetch = prefetched()
        await expect(takePrefetchedAppEntries("feed", request)).rejects.toThrow()
    })

    it("asks the server itself when there is no prefetched response or it failed", async () => {
        const fetched = async () => "fetched"
        expect(await prefetchedOr(undefined, fetched)).toBe("fetched")
        expect(await prefetchedOr(Promise.resolve("prefetched"), fetched)).toBe("prefetched")
        expect(await prefetchedOr(Promise.reject(new Error("401")), fetched)).toBe("fetched")
    })
})
