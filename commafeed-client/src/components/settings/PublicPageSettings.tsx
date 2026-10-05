import { msg } from "@lingui/core/macro"
import { useLingui } from "@lingui/react"
import { Trans } from "@lingui/react/macro"
import {
    Accordion,
    ActionIcon,
    Badge,
    Box,
    Button,
    Checkbox,
    CopyButton,
    Group,
    Stack,
    Switch,
    Text,
    TextInput,
    Tooltip,
} from "@mantine/core"
import { useForm } from "@mantine/form"
import { openConfirmModal } from "@mantine/modals"
import { useState } from "react"
import { useAsync, useAsyncCallback } from "react-async-hook"
import { TbCheck, TbCopy, TbDeviceFloppy, TbExternalLink, TbPlus, TbRefresh, TbTrash } from "react-icons/tb"
import { client, errorToStrings } from "@/app/client"
import { redirectToSelectedSource } from "@/app/redirect/thunks"
import { useAppDispatch, useAppSelector } from "@/app/store"
import type { Category, PublicPageSettings as PublicPageSettingsModel } from "@/app/types"
import { Alert } from "@/components/Alert"
import { Loader } from "@/components/Loader"

export function publicPageUrl(token: string) {
    return `${window.location.origin}${window.location.pathname}#/public/${encodeURIComponent(token)}`
}

function CategoryCheckboxes(
    props: Readonly<{
        categories: Category[]
        selectedIds: number[]
        disabled: boolean
        onToggle: (id: number, checked: boolean) => void
    }>
) {
    if (props.categories.length === 0) return null
    return (
        <Stack gap="xs">
            {props.categories.map(category => {
                const id = Number(category.id)
                return (
                    <Box key={category.id}>
                        <Checkbox
                            label={category.name}
                            checked={props.selectedIds.includes(id)}
                            disabled={props.disabled}
                            onChange={e => props.onToggle(id, e.currentTarget.checked)}
                        />
                        {category.children.length > 0 && (
                            <Box pl="xl" pt="xs">
                                <CategoryCheckboxes
                                    categories={category.children}
                                    selectedIds={props.selectedIds}
                                    disabled={props.disabled}
                                    onToggle={props.onToggle}
                                />
                            </Box>
                        )}
                    </Box>
                )
            })}
        </Stack>
    )
}

function PublicPageForm(
    props: Readonly<{
        page: PublicPageSettingsModel
        rootCategory: Category
        onChange: () => Promise<unknown>
    }>
) {
    const { page, rootCategory, onChange } = props
    const { _ } = useLingui()

    const saveSettings = useAsyncCallback(async (values: PublicPageSettingsModel) => {
        await client.user.savePublicPage({ ...values, id: page.id })
        await onChange()
    })
    const regenerateToken = useAsyncCallback(async (id: number) => {
        await client.user.regeneratePublicPageToken({ id })
        await onChange()
    })
    const deletePage = useAsyncCallback(async (id: number) => {
        await client.user.deletePublicPage({ id })
        await onChange()
    })

    const form = useForm<PublicPageSettingsModel>({ initialValues: page })

    const toggleCategory = (id: number, checked: boolean) => {
        const ids = form.values.categoryIds.filter(i => i !== id)
        form.setFieldValue("categoryIds", checked ? [...ids, id] : ids)
    }

    const openDeleteModal = (id: number) =>
        openConfirmModal({
            title: <Trans>Delete public page</Trans>,
            children: (
                <Text size="sm">
                    <Trans>Are you sure you want to delete this public page? Its address will stop working.</Trans>
                </Text>
            ),
            labels: { confirm: <Trans>Delete</Trans>, cancel: <Trans>Cancel</Trans> },
            confirmProps: { color: "red" },
            onConfirm: async () => await deletePage.execute(id),
        })

    const url = page.token ? publicPageUrl(page.token) : undefined
    return (
        <form onSubmit={form.onSubmit(saveSettings.execute)}>
            <Stack>
                {saveSettings.status === "success" && <Alert level="success" messages={[_(msg`Public page settings saved.`)]} />}
                {saveSettings.status === "error" && <Alert level="error" messages={errorToStrings(saveSettings.error)} />}
                {regenerateToken.status === "error" && <Alert level="error" messages={errorToStrings(regenerateToken.error)} />}
                {deletePage.status === "error" && <Alert level="error" messages={errorToStrings(deletePage.error)} />}

                <TextInput
                    label={<Trans>Name</Trans>}
                    description={<Trans>Optional, shown next to CommaFeed at the top of the public page.</Trans>}
                    maxLength={128}
                    {...form.getInputProps("name")}
                    value={form.values.name ?? ""}
                />

                <Switch label={<Trans>Enable public page</Trans>} {...form.getInputProps("enabled", { type: "checkbox" })} />

                {url && form.values.enabled && (
                    <TextInput
                        label={<Trans>Public page address</Trans>}
                        description={
                            <Trans>
                                Anyone with this address can read the selected categories. It doesn't contain your user name. Generate a new
                                address if you want the current one to stop working.
                            </Trans>
                        }
                        value={url}
                        readOnly
                        rightSectionWidth={64}
                        rightSection={
                            <Group gap={4} wrap="nowrap">
                                <CopyButton value={url}>
                                    {({ copied, copy }) => (
                                        <Tooltip label={copied ? <Trans>Copied</Trans> : <Trans>Copy</Trans>}>
                                            <ActionIcon variant="subtle" onClick={copy}>
                                                {copied ? <TbCheck size={16} /> : <TbCopy size={16} />}
                                            </ActionIcon>
                                        </Tooltip>
                                    )}
                                </CopyButton>
                                <Tooltip label={<Trans>Open</Trans>}>
                                    <ActionIcon variant="subtle" component="a" href={url} target="_blank" rel="noreferrer">
                                        <TbExternalLink size={16} />
                                    </ActionIcon>
                                </Tooltip>
                            </Group>
                        }
                    />
                )}

                <Box>
                    <Text fw={500} size="sm">
                        <Trans>Categories shown on the public page</Trans>
                    </Text>
                    <Text c="dimmed" size="xs" pb="sm">
                        <Trans>
                            Only the feeds that are directly in a selected category are shown. Subcategories have to be selected
                            individually.
                        </Trans>
                    </Text>
                    <Stack gap="xs">
                        <CategoryCheckboxes
                            categories={rootCategory.children}
                            selectedIds={form.values.categoryIds}
                            disabled={!form.values.enabled}
                            onToggle={toggleCategory}
                        />
                        <Checkbox
                            label={<Trans>Feeds without a category</Trans>}
                            disabled={!form.values.enabled}
                            {...form.getInputProps("showUncategorized", { type: "checkbox" })}
                        />
                    </Stack>
                    {rootCategory.children.length === 0 && (
                        <Text c="dimmed" size="sm" pt="xs">
                            <Trans>You don't have any categories yet.</Trans>
                        </Text>
                    )}
                </Box>

                {page.id !== undefined && (
                    <Group>
                        <Button type="submit" leftSection={<TbDeviceFloppy size={16} />} loading={saveSettings.loading}>
                            <Trans>Save</Trans>
                        </Button>
                        <Button
                            variant="outline"
                            color="red"
                            leftSection={<TbRefresh size={16} />}
                            loading={regenerateToken.loading}
                            onClick={async () => page.id !== undefined && (await regenerateToken.execute(page.id))}
                        >
                            <Trans>Generate new address</Trans>
                        </Button>
                        <Button
                            variant="outline"
                            color="red"
                            leftSection={<TbTrash size={16} />}
                            loading={deletePage.loading}
                            onClick={() => page.id !== undefined && openDeleteModal(page.id)}
                        >
                            <Trans>Delete</Trans>
                        </Button>
                    </Group>
                )}
            </Stack>
        </form>
    )
}

export function PublicPageSettings() {
    const rootCategory = useAppSelector(state => state.tree.rootCategory)
    const dispatch = useAppDispatch()
    const [openedPage, setOpenedPage] = useState<string | null>(null)

    // keep showing the current pages while reloading them, so that the forms keep their state (e.g. the "saved" message)
    const pages = useAsync(client.user.getPublicPages, [], { setLoading: state => ({ ...state, loading: true }) })
    const addPage = useAsyncCallback(async () => {
        const result = await client.user.savePublicPage({ enabled: false, showUncategorized: false, categoryIds: [] })
        await pages.execute()
        setOpenedPage(String(result.data))
    })

    if (!pages.result || !rootCategory) return <Loader />

    return (
        <Stack>
            <Text size="sm">
                <Trans>
                    A public page is a read-only page that anyone can open without logging in. It only shows the entries of the categories
                    selected for it. Nothing else (settings, feed management, read status, starred entries, tags, ...) is accessible from
                    it. Each public page has its own address and its own selection of categories.
                </Trans>
            </Text>

            {addPage.status === "error" && <Alert level="error" messages={errorToStrings(addPage.error)} />}

            {pages.result.data.length === 0 && (
                <Text c="dimmed" size="sm">
                    <Trans>You don't have any public pages yet.</Trans>
                </Text>
            )}

            {pages.result.data.length > 0 && (
                <Accordion variant="separated" value={openedPage} onChange={setOpenedPage}>
                    {pages.result.data.map(page => (
                        <Accordion.Item key={page.id} value={String(page.id)}>
                            <Accordion.Control>
                                <Group justify="space-between" wrap="nowrap" pr="sm">
                                    <Text truncate>{page.name || <Trans>Unnamed public page</Trans>}</Text>
                                    {page.enabled ? (
                                        <Badge color="green" variant="light">
                                            <Trans>Enabled</Trans>
                                        </Badge>
                                    ) : (
                                        <Badge color="gray" variant="light">
                                            <Trans>Disabled</Trans>
                                        </Badge>
                                    )}
                                </Group>
                            </Accordion.Control>
                            <Accordion.Panel>
                                <PublicPageForm page={page} rootCategory={rootCategory} onChange={pages.execute} />
                            </Accordion.Panel>
                        </Accordion.Item>
                    ))}
                </Accordion>
            )}

            <Group>
                <Button variant="default" onClick={async () => await dispatch(redirectToSelectedSource())}>
                    <Trans>Cancel</Trans>
                </Button>
                <Button variant="outline" leftSection={<TbPlus size={16} />} loading={addPage.loading} onClick={addPage.execute}>
                    <Trans>Add public page</Trans>
                </Button>
            </Group>
        </Stack>
    )
}
