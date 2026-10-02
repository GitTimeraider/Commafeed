import { Trans } from "@lingui/react/macro"
import {
    Anchor,
    AppShell,
    Box,
    Burger,
    Button,
    Center,
    Container,
    Divider,
    Group,
    NavLink,
    Paper,
    ScrollArea,
    Stack,
    Text,
    Title,
} from "@mantine/core"
import { useDisclosure } from "@mantine/hooks"
import { useEffect, useRef, useState } from "react"
import { useAsync } from "react-async-hook"
import { TbFolder, TbLayoutList } from "react-icons/tb"
import { Link, useParams } from "react-router-dom"
import { client } from "@/app/client"
import { Constants } from "@/app/constants"
import type { Entry, PublicCategory, PublicEntriesSourceType } from "@/app/types"
import { FeedEntryBody } from "@/components/content/FeedEntryBody"
import { FeedFavicon } from "@/components/content/FeedFavicon"
import { Loader } from "@/components/Loader"
import { Logo } from "@/components/Logo"
import { RelativeDate } from "@/components/RelativeDate"

const PAGE_SIZE = 20

function sourcePath(userName: string, type: PublicEntriesSourceType, id: string) {
    return `/public/${encodeURIComponent(userName)}/${type}/${id}`
}

function PublicTreeCategory(
    props: Readonly<{
        userName: string
        category: PublicCategory
        selectedType: PublicEntriesSourceType
        selectedId: string
        onNavigate: () => void
    }>
) {
    const { category, userName, selectedType, selectedId } = props
    const hasContent = category.children.length > 0 || category.feeds.length > 0
    return (
        <NavLink
            label={category.name}
            leftSection={<TbFolder size={18} />}
            component={Link}
            to={sourcePath(userName, "category", category.id)}
            active={selectedType === "category" && selectedId === category.id}
            onClick={props.onNavigate}
            // categories are always expanded, clicking on a category only shows its entries
            opened
            rightSection={null}
            childrenOffset="md"
        >
            {hasContent ? <PublicTreeChildren {...props} /> : undefined}
        </NavLink>
    )
}

function PublicTreeChildren(
    props: Readonly<{
        userName: string
        category: PublicCategory
        selectedType: PublicEntriesSourceType
        selectedId: string
        onNavigate: () => void
    }>
) {
    const { category, userName, selectedType, selectedId } = props
    return (
        <>
            {category.children.map(child => (
                <PublicTreeCategory key={child.id} {...props} category={child} />
            ))}
            {category.feeds.map(feed => (
                <NavLink
                    key={feed.id}
                    label={feed.name}
                    leftSection={<FeedFavicon url={feed.iconUrl} />}
                    component={Link}
                    to={sourcePath(userName, "feed", String(feed.id))}
                    active={selectedType === "feed" && selectedId === String(feed.id)}
                    onClick={props.onNavigate}
                />
            ))}
        </>
    )
}

function PublicEntry({ entry }: Readonly<{ entry: Entry }>) {
    return (
        <Paper withBorder p="md" radius="md">
            <Group gap="xs" wrap="nowrap" pb="xs">
                <Box style={{ flexShrink: 0 }}>
                    <FeedFavicon url={entry.iconUrl} />
                </Box>
                <Text size="xs" c="dimmed" lineClamp={1}>
                    {entry.feedName}
                    {" · "}
                    <RelativeDate date={entry.date} />
                    {entry.author && ` · ${entry.author}`}
                </Text>
            </Group>
            <Title order={4} pb="sm" dir={entry.rtl ? "rtl" : undefined}>
                {entry.url ? (
                    <Anchor href={entry.url} target="_blank" rel="noreferrer" c="inherit">
                        {entry.title || entry.url}
                    </Anchor>
                ) : (
                    entry.title
                )}
            </Title>
            <Box dir={entry.rtl ? "rtl" : undefined} style={{ maxWidth: Constants.layout.entryMaxWidth }}>
                <FeedEntryBody entry={entry} />
            </Box>
        </Paper>
    )
}

function PublicEntries(
    props: Readonly<{
        userName: string
        type: PublicEntriesSourceType
        id: string
    }>
) {
    const { userName, type, id } = props
    const [entries, setEntries] = useState<Entry[]>([])
    const [name, setName] = useState<string>()
    const [hasMore, setHasMore] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(false)
    // used to ignore responses of requests made for a previously selected source
    const requestCounter = useRef(0)

    const load = async (offset: number) => {
        const request = ++requestCounter.current
        setLoading(true)
        setError(false)
        try {
            const result = await client.publicPage.getEntries(userName, { type, id, offset, limit: PAGE_SIZE })
            if (request !== requestCounter.current) return
            setName(result.data.name)
            setEntries(current => (offset === 0 ? result.data.entries : [...current, ...result.data.entries]))
            setHasMore(result.data.hasMore)
        } catch {
            if (request === requestCounter.current) {
                setError(true)
                setHasMore(false)
            }
        } finally {
            if (request === requestCounter.current) setLoading(false)
        }
    }

    // biome-ignore lint/correctness/useExhaustiveDependencies: reload only when the source changes
    useEffect(() => {
        setEntries([])
        setName(undefined)
        setHasMore(false)
        window.scrollTo(0, 0)
        load(0)
    }, [userName, type, id])

    return (
        <Stack>
            {name && <Title order={2}>{name}</Title>}
            {entries.map(entry => (
                <PublicEntry key={`${entry.feedId}-${entry.id}`} entry={entry} />
            ))}
            {error && (
                <Text c="red">
                    <Trans>Could not load entries.</Trans>
                </Text>
            )}
            {!loading && !error && entries.length === 0 && (
                <Text c="dimmed">
                    <Trans>No entries</Trans>
                </Text>
            )}
            {loading && <Loader />}
            {!loading && hasMore && (
                <Center>
                    <Button variant="default" onClick={async () => await load(entries.length)}>
                        <Trans>Load more</Trans>
                    </Button>
                </Center>
            )}
        </Stack>
    )
}

export function PublicPage() {
    const params = useParams()
    const userName = params.userName ?? ""
    const type: PublicEntriesSourceType = params.type === "feed" ? "feed" : "category"
    const id = params.id ?? Constants.categories.all.id
    const [navbarOpened, { toggle: toggleNavbar, close: closeNavbar }] = useDisclosure(false)

    const tree = useAsync(async () => (await client.publicPage.getTree(userName)).data, [userName])

    if (tree.loading) return <Loader />

    if (tree.error || !tree.result) {
        return (
            <Container size="xs" pt="xl">
                <Center pb="md">
                    <Logo size={48} />
                </Center>
                <Text ta="center">
                    <Trans>This public page does not exist or is not enabled.</Trans>
                </Text>
            </Container>
        )
    }

    const root = tree.result
    return (
        <AppShell
            header={{ height: Constants.layout.headerHeight }}
            navbar={{ width: 320, breakpoint: Constants.layout.mobileBreakpointName, collapsed: { mobile: !navbarOpened } }}
            padding="md"
        >
            <AppShell.Header>
                <Group h="100%" px="md" wrap="nowrap">
                    <Burger opened={navbarOpened} onClick={toggleNavbar} hiddenFrom={Constants.layout.mobileBreakpointName} size="sm" />
                    <Logo size={24} />
                    <Title order={3} lineClamp={1}>
                        CommaFeed - {root.name}
                    </Title>
                </Group>
            </AppShell.Header>

            <AppShell.Navbar>
                <ScrollArea type="auto">
                    <NavLink
                        label={<Trans>All</Trans>}
                        leftSection={<TbLayoutList size={18} />}
                        component={Link}
                        to={sourcePath(userName, "category", Constants.categories.all.id)}
                        active={type === "category" && id === Constants.categories.all.id}
                        onClick={closeNavbar}
                    />
                    <Divider />
                    <PublicTreeChildren userName={userName} category={root} selectedType={type} selectedId={id} onNavigate={closeNavbar} />
                </ScrollArea>
            </AppShell.Navbar>

            <AppShell.Main>
                <Container size={Constants.layout.entryMaxWidth} px={0}>
                    <PublicEntries userName={userName} type={type} id={id} />
                </Container>
            </AppShell.Main>
        </AppShell>
    )
}
