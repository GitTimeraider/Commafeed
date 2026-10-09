import { i18n } from "@lingui/core"
import { I18nProvider } from "@lingui/react"
import { MantineProvider, v8CssVariablesResolver } from "@mantine/core"
import { ModalsProvider } from "@mantine/modals"
import { Notifications } from "@mantine/notifications"
import type React from "react"
import { lazy, Suspense, useEffect } from "react"
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom"
import { Constants } from "@/app/constants"
import { redirectTo } from "@/app/redirect/slice"
import { redirectToInitialSetup } from "@/app/redirect/thunks"
import { reloadServerInfos } from "@/app/server/thunks"
import { useAppDispatch, useAppSelector } from "@/app/store"
import { ErrorBoundary } from "@/components/ErrorBoundary"
import { Header } from "@/components/header/Header"
import { Loader } from "@/components/Loader"
import { Tree } from "@/components/sidebar/Tree"
import { useI18n } from "@/i18n"
import { AccessRestrictedPage } from "@/pages/AccessRestrictedPage"
import { FeedEntriesPage } from "@/pages/app/FeedEntriesPage"
import Layout from "@/pages/app/Layout"
import { PublicPage } from "@/pages/PublicPage"

// Pages that are rarely opened are loaded when needed, so that the pages people read their feeds on (including public
// pages, often embedded in an iframe of another website) download and start faster. The feed details page alone
// brings the filtering expression editor, the largest library of the app.
function lazyPage<P extends object>(load: () => Promise<React.ComponentType<P>>) {
    const Page = lazy(async () => ({ default: await load() }))
    return function LazyPage(props: P) {
        return (
            <Suspense fallback={<Loader />}>
                <Page {...props} />
            </Suspense>
        )
    }
}

const AdminUsersPage = lazyPage(async () => (await import("@/pages/admin/AdminUsersPage")).AdminUsersPage)
const MetricsPage = lazyPage(async () => (await import("@/pages/admin/MetricsPage")).MetricsPage)
const AboutPage = lazyPage(async () => (await import("@/pages/app/AboutPage")).AboutPage)
const AddPage = lazyPage(async () => (await import("@/pages/app/AddPage")).AddPage)
const CategoryDetailsPage = lazyPage(async () => (await import("@/pages/app/CategoryDetailsPage")).CategoryDetailsPage)
const FeedDetailsPage = lazyPage(async () => (await import("@/pages/app/FeedDetailsPage")).FeedDetailsPage)
const SettingsPage = lazyPage(async () => (await import("@/pages/app/SettingsPage")).SettingsPage)
const TagDetailsPage = lazyPage(async () => (await import("@/pages/app/TagDetailsPage")).TagDetailsPage)
const InitialSetupPage = lazyPage(async () => (await import("@/pages/auth/InitialSetupPage")).InitialSetupPage)
const LoginPage = lazyPage(async () => (await import("@/pages/auth/LoginPage")).LoginPage)
const PasswordRecoveryPage = lazyPage(async () => (await import("@/pages/auth/PasswordRecoveryPage")).PasswordRecoveryPage)
const PasswordResetPage = lazyPage(async () => (await import("@/pages/auth/PasswordResetPage")).PasswordResetPage)
const RegistrationPage = lazyPage(async () => (await import("@/pages/auth/RegistrationPage")).RegistrationPage)
const WelcomePage = lazyPage(async () => (await import("@/pages/WelcomePage")).WelcomePage)

function Providers(
    props: Readonly<{
        children: React.ReactNode
    }>
) {
    const primaryColor = useAppSelector(state => state.user.settings?.primaryColor) || Constants.theme.defaultPrimaryColor
    return (
        <I18nProvider i18n={i18n}>
            <MantineProvider
                defaultColorScheme="auto"
                // keep using css variables from mantine v8
                cssVariablesResolver={v8CssVariablesResolver}
                theme={{
                    primaryColor: primaryColor,
                    fontFamily: "Open Sans",
                    colors: {
                        // keep using dark colors from mantine v6
                        // https://v6.mantine.dev/theming/colors/#default-colors
                        dark: [
                            "#C1C2C5",
                            "#A6A7AB",
                            "#909296",
                            "#5c5f66",
                            "#373A40",
                            "#2C2E33",
                            "#25262b",
                            "#1A1B1E",
                            "#141517",
                            "#101113",
                        ],
                    },
                }}
            >
                <ModalsProvider>
                    <Notifications position="bottom-right" zIndex={9999} />
                    <ErrorBoundary>{props.children}</ErrorBoundary>
                </ModalsProvider>
            </MantineProvider>
        </I18nProvider>
    )
}

function AppRoutes() {
    const sidebarVisible = useAppSelector(state => state.tree.sidebarVisible)
    const accessRestricted = useAppSelector(state => state.server.serverInfos?.accessRestricted)
    const location = useLocation()

    // clients outside of the allowed networks can only open public pages, the server refuses everything else
    if (accessRestricted && !location.pathname.startsWith("/public/")) {
        return <AccessRestrictedPage />
    }

    return (
        <Routes>
            <Route path="/" element={<Navigate to={`/app/category/${Constants.categories.all.id}`} replace />} />
            <Route path="welcome" element={<WelcomePage />} />
            <Route path="setup" element={<InitialSetupPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegistrationPage />} />
            <Route path="passwordRecovery" element={<PasswordRecoveryPage />} />
            <Route path="passwordReset" element={<PasswordResetPage />} />
            <Route path="public/:token" element={<PublicPage />} />
            <Route path="public/:token/:type/:id" element={<PublicPage />} />
            <Route path="app" element={<Layout header={<Header />} sidebar={<Tree />} sidebarVisible={sidebarVisible} />}>
                <Route path="category">
                    <Route path=":id" element={<FeedEntriesPage sourceType="category" />} />
                    <Route path=":id/details" element={<CategoryDetailsPage />} />
                </Route>
                <Route path="feed">
                    <Route path=":id" element={<FeedEntriesPage sourceType="feed" />} />
                    <Route path=":id/details" element={<FeedDetailsPage />} />
                </Route>
                <Route path="tag">
                    <Route path=":id" element={<FeedEntriesPage sourceType="tag" />} />
                    <Route path=":id/details" element={<TagDetailsPage />} />
                </Route>
                <Route path="add" element={<AddPage />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="admin">
                    <Route path="users" element={<AdminUsersPage />} />
                    <Route path="metrics" element={<MetricsPage />} />
                </Route>
                <Route path="about" element={<AboutPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}

function InitialSetupHandler() {
    const serverInfos = useAppSelector(state => state.server.serverInfos)
    const dispatch = useAppDispatch()
    useEffect(() => {
        if (serverInfos?.initialSetupRequired) {
            dispatch(redirectToInitialSetup())
        }
    }, [serverInfos, dispatch])

    return null
}

function RedirectHandler() {
    const target = useAppSelector(state => state.redirect.to)
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    useEffect(() => {
        if (target) {
            // pages can subscribe to state.timestamp in order to refresh when navigating to an url matching the current page
            navigate(target, { state: { timestamp: new Date() } })
            dispatch(redirectTo(undefined))
        }
    }, [target, dispatch, navigate])

    return null
}

export function App() {
    useI18n()
    const dispatch = useAppDispatch()

    useEffect(() => {
        dispatch(reloadServerInfos())
    }, [dispatch])

    return (
        <Providers>
            <HashRouter>
                <InitialSetupHandler />
                <RedirectHandler />
                <AppRoutes />
            </HashRouter>
        </Providers>
    )
}
