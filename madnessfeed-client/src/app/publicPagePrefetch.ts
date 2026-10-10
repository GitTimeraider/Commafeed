import type { Entries, PublicCategory, PublicEntriesSourceType } from "@/app/types"

// index.html requests the tree and the first entries of a public page before the scripts have downloaded. The page takes
// each response once, when it is for the page it shows, and asks the server itself otherwise.
interface PublicPagePrefetch {
    token: string
    type: PublicEntriesSourceType
    id: string
    tree?: Promise<PublicCategory>
    entries?: Promise<Entries>
}

declare global {
    interface Window {
        madnessFeedPublicPagePrefetch?: PublicPagePrefetch
    }
}

export function takePrefetchedTree(token: string): Promise<PublicCategory> | undefined {
    const prefetch = window.madnessFeedPublicPagePrefetch
    if (prefetch?.token !== token) return undefined
    const tree = prefetch.tree
    prefetch.tree = undefined
    return tree
}

export function takePrefetchedEntries(token: string, type: PublicEntriesSourceType, id: string): Promise<Entries> | undefined {
    const prefetch = window.madnessFeedPublicPagePrefetch
    if (prefetch?.token !== token || prefetch.type !== type || prefetch.id !== id) return undefined
    const entries = prefetch.entries
    prefetch.entries = undefined
    return entries
}
