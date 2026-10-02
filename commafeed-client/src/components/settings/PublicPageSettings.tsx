import { msg } from "@lingui/core/macro"
import { useLingui } from "@lingui/react"
import { Trans } from "@lingui/react/macro"
import { ActionIcon, Box, Button, Checkbox, CopyButton, Group, Stack, Switch, Text, TextInput, Tooltip } from "@mantine/core"
import { useForm } from "@mantine/form"
import { useEffect } from "react"
import { useAsync, useAsyncCallback } from "react-async-hook"
import { TbCheck, TbCopy, TbDeviceFloppy, TbExternalLink } from "react-icons/tb"
import { client, errorToStrings } from "@/app/client"
import { redirectToSelectedSource } from "@/app/redirect/thunks"
import { useAppDispatch, useAppSelector } from "@/app/store"
import type { Category, PublicPageSettings as PublicPageSettingsModel } from "@/app/types"
import { Alert } from "@/components/Alert"
import { Loader } from "@/components/Loader"

export function publicPageUrl(userName: string) {
    return `${window.location.origin}${window.location.pathname}#/public/${encodeURIComponent(userName)}`
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

export function PublicPageSettings() {
    const userName = useAppSelector(state => state.user.profile?.name)
    const rootCategory = useAppSelector(state => state.tree.rootCategory)
    const { _ } = useLingui()
    const dispatch = useAppDispatch()

    const settings = useAsync(client.user.getPublicPageSettings, [])
    const saveSettings = useAsyncCallback(client.user.savePublicPageSettings)

    const form = useForm<PublicPageSettingsModel>({
        initialValues: { enabled: false, showUncategorized: false, categoryIds: [] },
    })
    useEffect(() => {
        if (settings.result) form.initialize(settings.result.data)
    }, [form.initialize, settings.result])

    const toggleCategory = (id: number, checked: boolean) => {
        const ids = form.values.categoryIds.filter(i => i !== id)
        form.setFieldValue("categoryIds", checked ? [...ids, id] : ids)
    }

    if (!settings.result || !rootCategory) return <Loader />

    const url = userName ? publicPageUrl(userName) : undefined
    return (
        <form onSubmit={form.onSubmit(saveSettings.execute)}>
            <Stack>
                {saveSettings.status === "success" && <Alert level="success" messages={[_(msg`Public page settings saved.`)]} />}
                {saveSettings.status === "error" && <Alert level="error" messages={errorToStrings(saveSettings.error)} />}

                <Switch
                    label={<Trans>Enable public page</Trans>}
                    description={
                        <Trans>
                            The public page is a read-only page that anyone can open without logging in. It only shows the entries of the
                            categories selected below. Nothing else (settings, feed management, read status, starred entries, tags, ...) is
                            accessible from it.
                        </Trans>
                    }
                    {...form.getInputProps("enabled", { type: "checkbox" })}
                />

                {url && form.values.enabled && (
                    <TextInput
                        label={<Trans>Public page address</Trans>}
                        description={<Trans>Save the settings before sharing this address.</Trans>}
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

                <Group>
                    <Button variant="default" onClick={async () => await dispatch(redirectToSelectedSource())}>
                        <Trans>Cancel</Trans>
                    </Button>
                    <Button type="submit" leftSection={<TbDeviceFloppy size={16} />} loading={saveSettings.loading}>
                        <Trans>Save</Trans>
                    </Button>
                </Group>
            </Stack>
        </form>
    )
}
