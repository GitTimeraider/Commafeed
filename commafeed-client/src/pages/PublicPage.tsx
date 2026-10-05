import { Trans } from "@lingui/react/macro"
import {
    ActionIcon,
    Anchor,
    AppShell,
    Box,
    Burger,
    Button,
    Center,
    Container,
    Divider,
    Flex,
    Group,
    NavLink,
    Paper,
    ScrollArea,
    Space,
    Stack,
    Text,
    Title,
    Tooltip,
} from "@mantine/core"
import { useDisclosure } from "@mantine/hooks"
import type React from "react"
import { useEffect, useRef, useState } from "react"
import { useAsync } from "react-async-hook"
import { TbExternalLink, TbFolder, TbLayoutList } from "react-icons/tb"
import { Link, useParams } from "react-router-dom"
import { client } from "@/app/client"
import { Constants } from "@/app/constants"
import type { Entry, PublicCategory, PublicEntriesSourceType } from "@/app/types"
import { FeedEntryBody } from "@/components/content/FeedEntryBody"
import { FeedFavicon } from "@/components/content/FeedFavicon"
import { Loader } from "@/components/Loader"
import { Logo } from "@/components/Logo"
import { RelativeDate } from "@/components/RelativeDate"
import { tss } from "@/tss"

const PAGE_SIZE = 20

function sourcePath(token: string, type: PublicEntriesSourceType, id: string) {
    return `/public/${encodeURIComponent(token)}/${type}/${id}`
}

interface TreeProps {
    token: string
    category: PublicCategory
    selectedType: PublicEntriesSourceType
    selectedId: string
    onNavigate: () => void
}

function PublicTreeChildren(props: Readonly<TreeProps>) {
    const { category, token, selectedType, selectedId } = props
    return (
        <>
            {category.children.map(child => (
                <Box key={child.id}>
                    {/* children are rendered next to the NavLink instead of inside it, a NavLink with children only toggles its
                    children when clicked and doesn't navigate */}
                    <NavLink
                        label={child.name}
                        leftSection={<TbFolder size={18} />}
                        component={Link}
                        to={sourcePath(token, "category", child.id)}
                        active={selectedType === "category" && selectedId === child.id}
                        onClick={props.onNavigate}
                    />
                    <Box pl="md">
                        <PublicTreeChildren {...props} category={child} />
                    </Box>
                </Box>
            ))}
            {category.feeds.map(feed => (
                <NavLink
                    key={feed.id}
                    label={feed.name}
                    leftSection={<FeedFavicon url={feed.iconUrl} />}
                    component={Link}
                    to={sourcePath(token, "feed", String(feed.id))}
                    active={selectedType === "feed" && selectedId === String(feed.id)}
                    onClick={props.onNavigate}
                />
            ))}
        </>
    )
}

const useEntryStyles = tss.withParams<{ expanded: boolean; rtl: boolean }>().create(({ theme, colorScheme, expanded, rtl }) => ({
    paper: {
        marginTop: 10,
        marginBottom: 10,
        [`@media (max-width: ${Constants.layout.mobileBreakpoint}px)`]: {
            marginTop: 6,
            marginBottom: 6,
        },
        "@media (hover: hover)": {
            "&:hover": {
                backgroundColor: expanded ? undefined : colorScheme === "dark" ? theme.colors.dark[6] : theme.colors.gray[1],
            },
        },
    },
    headerLink: {
        color: "inherit",
        textDecoration: "none",
    },
    title: {
        fontWeight: expanded ? "inherit" : "bold",
    },
    body: {
        direction: rtl ? "rtl" : "ltr",
        maxWidth: Constants.layout.entryMaxWidth,
    },
}))

// same look as the "detailed" display mode of the application: title, feed and date, the content is shown when clicking on the entry
function PublicEntry({ entry }: Readonly<{ entry: Entry }>) {
    const [expanded, setExpanded] = useState(false)
    const { classes } = useEntryStyles({ expanded, rtl: entry.rtl })

    const onHeaderClick = (e: React.MouseEvent) => {
        // let the browser open the link in a new tab on middle click or ctrl/cmd click
        if (e.button === 1 || e.ctrlKey || e.metaKey) return
        e.preventDefault()
        setExpanded(v => !v)
    }

    return (
        <Paper component="article" withBorder radius="sm" className={classes.paper}>
            <a className={classes.headerLink} href={entry.url} target="_blank" rel="noreferrer" onClick={onHeaderClick}>
                <Box px="xs" py="xs">
                    <Flex align="flex-start" justify="space-between">
                        <Box className={classes.title}>{entry.title}</Box>
                        {entry.url && (
                            <Tooltip label={<Trans>Open link</Trans>} openDelay={Constants.tooltip.delay}>
                                <ActionIcon
                                    // a link can't be nested in the header link
                                    component="span"
                                    variant="transparent"
                                    c="dimmed"
                                    onClick={e => {
                                        e.preventDefault()
                                        e.stopPropagation()
                                        window.open(entry.url, "_blank", "noreferrer")
                                    }}
                                >
                                    <TbExternalLink size={18} />
                                </ActionIcon>
                            </Tooltip>
                        )}
                    </Flex>
                    <Flex align="center">
                        <FeedFavicon url={entry.iconUrl} />
                        <Space w={6} />
                        <Box c="dimmed">
                            {entry.feedName}
                            <span> · </span>
                            <RelativeDate date={entry.date} />
                        </Box>
                    </Flex>
                    {expanded && (entry.author || entry.categories) && (
                        <Box>
                            {entry.author && (
                                <span>
                                    <Trans>by</Trans> {entry.author}
                                </span>
                            )}
                            {entry.author && entry.categories && <span>&nbsp;·&nbsp;</span>}
                            {entry.categories && <span>{entry.categories}</span>}
                        </Box>
                    )}
                </Box>
            </a>
            {expanded && (
                <Box px="xs" pb="xs">
                    <Box className={`${classes.body} cf-content`}>
                        <FeedEntryBody entry={entry} />
                    </Box>
                    {entry.url && (
                        <>
                            <Divider variant="dashed" my="xs" />
                            <Anchor href={entry.url} target="_blank" rel="noreferrer" size="sm">
                                <Trans>Open link</Trans>
                            </Anchor>
                        </>
                    )}
                </Box>
            )}
        </Paper>
    )
}

function PublicEntries(
    props: Readonly<{
        token: string
        type: PublicEntriesSourceType
        id: string
        title: React.ReactNode
    }>
) {
    const { token, type, id } = props
    const [entries, setEntries] = useState<Entry[]>([])
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
            const result = await client.publicPage.getEntries(token, { type, id, offset, limit: PAGE_SIZE })
            if (request !== requestCounter.current) return
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
        setHasMore(false)
        window.scrollTo(0, 0)
        load(0)
    }, [token, type, id])

    return (
        <Box>
            <Title order={3} pb="xs">
                {props.title}
            </Title>
            {entries.map(entry => (
                <PublicEntry key={`${entry.feedId}-${entry.id}`} entry={entry} />
            ))}
            <Stack pt="md">
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
        </Box>
    )
}

function findSourceName(category: PublicCategory, type: PublicEntriesSourceType, id: string): string | undefined {
    if (type === "category" && category.id === id) return category.name
    if (type === "feed") {
        const feed = category.feeds.find(f => String(f.id) === id)
        if (feed) return feed.name
    }
    for (const child of category.children) {
        const name = findSourceName(child, type, id)
        if (name) return name
    }
    return undefined
}

export function PublicPage() {
    const params = useParams()
    const token = params.token ?? ""
    const type: PublicEntriesSourceType = params.type === "feed" ? "feed" : "category"
    const id = params.id ?? Constants.categories.all.id
    const [navbarOpened, { toggle: toggleNavbar, close: closeNavbar }] = useDisclosure(false)

    const tree = useAsync(async () => (await client.publicPage.getTree(token)).data, [token])

    const pageName = tree.result?.pageName
    useEffect(() => {
        if (!pageName) return
        const previousTitle = document.title
        document.title = `CommaFeed · ${pageName}`
        return () => {
            document.title = previousTitle
        }
    }, [pageName])

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
    const isAll = type === "category" && id === Constants.categories.all.id
    const title = isAll ? <Trans>All</Trans> : findSourceName(root, type, id)
    return (
        <AppShell
            header={{ height: Constants.layout.headerHeight }}
            navbar={{ width: 320, breakpoint: Constants.layout.mobileBreakpointName, collapsed: { mobile: !navbarOpened } }}
            padding={{ base: 6, [Constants.layout.mobileBreakpointName]: "md" }}
        >
            <AppShell.Header>
                <Group h="100%" px="md" wrap="nowrap">
                    <Burger opened={navbarOpened} onClick={toggleNavbar} hiddenFrom={Constants.layout.mobileBreakpointName} size="sm" />
                    <Logo size={24} />
                    {/* the name of the page is part of the title so it doesn't take any additional space */}
                    <Title order={3} miw={0} style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        CommaFeed
                        {root.pageName && (
                            <Text span inherit c="dimmed" fw="normal">
                                {" · "}
                                {root.pageName}
                            </Text>
                        )}
                    </Title>
                </Group>
            </AppShell.Header>

            <AppShell.Navbar>
                <ScrollArea type="auto">
                    <NavLink
                        label={<Trans>All</Trans>}
                        leftSection={<TbLayoutList size={18} />}
                        component={Link}
                        to={sourcePath(token, "category", Constants.categories.all.id)}
                        active={isAll}
                        onClick={closeNavbar}
                    />
                    <Divider />
                    <PublicTreeChildren token={token} category={root} selectedType={type} selectedId={id} onNavigate={closeNavbar} />
                </ScrollArea>
            </AppShell.Navbar>

            <AppShell.Main>
                {/* same as the application: entries use the full width, only the content of an entry has a maximum width */}
                <PublicEntries token={token} type={type} id={id} title={title} />
            </AppShell.Main>
        </AppShell>
    )
}
