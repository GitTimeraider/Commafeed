import { afterEach, describe, expect, it } from "vitest"
import { takePrefetchedEntries, takePrefetchedTree } from "@/app/publicPagePrefetch"
import type { Entries, PublicCategory } from "@/app/types"

describe("public page prefetch", () => {
    afterEach(() => {
        window.madnessFeedPublicPagePrefetch = undefined
    })

    const prefetch = () => {
        const tree = Promise.resolve({ id: "all" } as PublicCategory)
        const entries = Promise.resolve({ name: "all" } as Entries)
        window.madnessFeedPublicPagePrefetch = { token: "abc", type: "category", id: "all", tree, entries }
        return { tree, entries }
    }

    it("hands out each response once", () => {
        const { tree, entries } = prefetch()
        expect(takePrefetchedTree("abc")).toBe(tree)
        expect(takePrefetchedTree("abc")).toBeUndefined()
        expect(takePrefetchedEntries("abc", "category", "all")).toBe(entries)
        expect(takePrefetchedEntries("abc", "category", "all")).toBeUndefined()
    })

    it("ignores responses for another page or source", () => {
        prefetch()
        expect(takePrefetchedTree("other")).toBeUndefined()
        expect(takePrefetchedEntries("abc", "feed", "all")).toBeUndefined()
        expect(takePrefetchedEntries("abc", "category", "12")).toBeUndefined()
    })

    it("returns nothing when nothing was prefetched", () => {
        expect(takePrefetchedTree("abc")).toBeUndefined()
        expect(takePrefetchedEntries("abc", "category", "all")).toBeUndefined()
    })
})
