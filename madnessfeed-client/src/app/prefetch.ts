import type {
    Category,
    Entries,
    GetEntriesPaginatedRequest,
    PublicCategory,
    PublicEntriesSourceType,
    ServerInfo,
    Settings,
    UserModel,
} from "@/app/types"

// index.html asks for the data of the first screen before the scripts have downloaded (often in an iframe of another
// website, where every step that waits for the previous one shows). The page takes each response once, when it is for
// what it shows, and asks the server itself otherwise or when that request failed (e.g. not logged in).

interface PublicPagePrefetch {
    token: string
    type: PublicEntriesSourceType
    id: string
    tree?: Promise<PublicCategory>
    entries?: Promise<Entries>
}

interface AppPrefetch {
    settings?: Promise<Settings>
    profile?: Promise<UserModel>
    tree?: Promise<Category>
    tags?: Promise<string[]>
    // the entries of the category, feed or tag in the address, with the reading order and mode of the settings
    entries?: Promise<{ sourceType: string; request: GetEntriesPaginatedRequest; data: Entries }>
}

declare global {
    interface Window {
        madnessFeedServerInfosPrefetch?: Promise<ServerInfo>
        madnessFeedPublicPagePrefetch?: PublicPagePrefetch
        madnessFeedAppPrefetch?: AppPrefetch
    }
}

// the prefetched response if there is one, otherwise (or if it failed) the response of fetch
export async function prefetchedOr<T>(prefetched: Promise<T> | undefined, fetch: () => Promise<T>): Promise<T> {
    if (!prefetched) return await fetch()
    const result = await prefetched.catch(fetch)
    // A prefetched response is usually there already. Hand it over in a task of its own, so that the browser can show
    // what is ready (e.g. the layout) before the page draws the next part, instead of drawing everything at once.
    await new Promise(resolve => setTimeout(resolve, 0))
    return result
}

export function takePrefetchedServerInfos(): Promise<ServerInfo> | undefined {
    const serverInfos = window.madnessFeedServerInfosPrefetch
    window.madnessFeedServerInfosPrefetch = undefined
    return serverInfos
}

export function takePrefetchedPublicTree(token: string): Promise<PublicCategory> | undefined {
    const prefetch = window.madnessFeedPublicPagePrefetch
    if (prefetch?.token !== token) return undefined
    const tree = prefetch.tree
    prefetch.tree = undefined
    return tree
}

export function takePrefetchedPublicEntries(token: string, type: PublicEntriesSourceType, id: string): Promise<Entries> | undefined {
    const prefetch = window.madnessFeedPublicPagePrefetch
    if (prefetch?.token !== token || prefetch.type !== type || prefetch.id !== id) return undefined
    const entries = prefetch.entries
    prefetch.entries = undefined
    return entries
}

export function takePrefetchedApp<K extends Exclude<keyof AppPrefetch, "entries">>(key: K): AppPrefetch[K] {
    const prefetch = window.madnessFeedAppPrefetch
    const value = prefetch?.[key]
    if (prefetch) prefetch[key] = undefined
    return value
}

// the prefetched entries, when they were asked for with exactly this request
export function takePrefetchedAppEntries(sourceType: string, request: GetEntriesPaginatedRequest): Promise<Entries> | undefined {
    const prefetch = window.madnessFeedAppPrefetch
    const entries = prefetch?.entries
    if (!prefetch || !entries) return undefined
    prefetch.entries = undefined
    const key = (r: GetEntriesPaginatedRequest) =>
        JSON.stringify([r.id, r.order, r.readType, r.offset ?? 0, r.limit, r.tag ?? null, r.keywords || null])
    return entries.then(prefetched => {
        if (prefetched.sourceType !== sourceType || key(prefetched.request) !== key(request)) throw new Error("other entries")
        return prefetched.data
    })
}
