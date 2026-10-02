import { msg } from "@lingui/core/macro"
import { useLingui } from "@lingui/react"
import { Trans } from "@lingui/react/macro"
import {
    ActionIcon,
    Badge,
    Box,
    Button,
    Code,
    CopyButton,
    Divider,
    Group,
    Modal,
    PasswordInput,
    Stack,
    Table,
    Text,
    TextInput,
    Title,
    Tooltip,
} from "@mantine/core"
import { QRCodeSVG } from "qrcode.react"
import { useState } from "react"
import { useAsync, useAsyncCallback } from "react-async-hook"
import { TbCheck, TbCopy, TbDeviceMobile, TbKey, TbPlus, TbTrash } from "react-icons/tb"
import { client, errorToStrings } from "@/app/client"
import type { TotpSetupResponse } from "@/app/types"
import { createPasskey, passkeysSupported } from "@/app/webauthn"
import { Alert } from "@/components/Alert"
import { Loader } from "@/components/Loader"
import { RelativeDate } from "@/components/RelativeDate"

// asks for the current password before a sensitive action
function PasswordConfirmationModal(
    props: Readonly<{
        opened: boolean
        title: React.ReactNode
        onClose: () => void
        onConfirm: (password: string) => Promise<unknown>
    }>
) {
    const [password, setPassword] = useState("")
    const confirm = useAsyncCallback(props.onConfirm, {
        onSuccess: () => {
            setPassword("")
            props.onClose()
        },
    })

    return (
        <Modal opened={props.opened} onClose={props.onClose} title={props.title}>
            <form
                onSubmit={async e => {
                    e.preventDefault()
                    await confirm.execute(password)
                }}
            >
                <Stack>
                    {confirm.error && <Alert messages={errorToStrings(confirm.error)} />}
                    <PasswordInput
                        label={<Trans>Current password</Trans>}
                        value={password}
                        onChange={e => setPassword(e.currentTarget.value)}
                        required
                        data-autofocus
                    />
                    <Group justify="flex-end">
                        <Button variant="default" onClick={props.onClose}>
                            <Trans>Cancel</Trans>
                        </Button>
                        <Button type="submit" color="red" loading={confirm.loading}>
                            <Trans>Confirm</Trans>
                        </Button>
                    </Group>
                </Stack>
            </form>
        </Modal>
    )
}

function TotpSetup(props: Readonly<{ setup: TotpSetupResponse; onCancel: () => void; onEnabled: () => void }>) {
    const [code, setCode] = useState("")
    const enable = useAsyncCallback(client.mfa.enableTotp, { onSuccess: props.onEnabled })

    return (
        <form
            onSubmit={async e => {
                e.preventDefault()
                await enable.execute(code)
            }}
        >
            <Stack>
                {enable.error && <Alert messages={errorToStrings(enable.error)} />}
                <Text size="sm">
                    <Trans>
                        1. Scan this QR code with your authenticator app (e.g. Aegis, 2FAS, Google Authenticator, Microsoft Authenticator,
                        1Password, Bitwarden).
                    </Trans>
                </Text>
                <Box bg="white" p="sm" w="fit-content" style={{ borderRadius: 8 }}>
                    <QRCodeSVG value={props.setup.uri} size={180} marginSize={0} />
                </Box>
                <Text size="sm">
                    <Trans>Or enter this key manually:</Trans>
                </Text>
                <Group gap="xs" wrap="nowrap">
                    <Code style={{ wordBreak: "break-all" }}>{props.setup.secret}</Code>
                    <CopyButton value={props.setup.secret}>
                        {({ copied, copy }) => (
                            <ActionIcon variant="subtle" onClick={copy}>
                                {copied ? <TbCheck size={16} /> : <TbCopy size={16} />}
                            </ActionIcon>
                        )}
                    </CopyButton>
                </Group>
                <TextInput
                    label={<Trans>2. Enter the 6-digit code displayed by the app</Trans>}
                    placeholder="123456"
                    value={code}
                    onChange={e => setCode(e.currentTarget.value)}
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    maxLength={7}
                    required
                    maw={200}
                />
                <Group>
                    <Button variant="default" onClick={props.onCancel}>
                        <Trans>Cancel</Trans>
                    </Button>
                    <Button type="submit" loading={enable.loading}>
                        <Trans>Enable</Trans>
                    </Button>
                </Group>
            </Stack>
        </form>
    )
}

export function SecuritySettings() {
    const { _ } = useLingui()
    const status = useAsync(async () => (await client.mfa.getStatus()).data, [])

    const [totpSetup, setTotpSetup] = useState<TotpSetupResponse>()
    const startTotpSetup = useAsyncCallback(async () => setTotpSetup((await client.mfa.startTotpSetup()).data))
    const [disableTotpOpened, setDisableTotpOpened] = useState(false)

    const [passkeyName, setPasskeyName] = useState("")
    const [passkeyToDelete, setPasskeyToDelete] = useState<number>()
    const addPasskey = useAsyncCallback(
        async () => {
            const options = (await client.mfa.getPasskeyRegistrationOptions()).data
            const request = await createPasskey(options, passkeyName)
            await client.mfa.registerPasskey(request)
        },
        {
            onSuccess: async () => {
                setPasskeyName("")
                await status.execute()
            },
        }
    )

    if (!status.result) return <Loader />

    const passkeyError = addPasskey.error
        ? errorToStrings(addPasskey.error).length > 0
            ? errorToStrings(addPasskey.error)
            : [addPasskey.error.message]
        : []
    const mfaEnabled = status.result.totpEnabled || status.result.passkeys.length > 0

    return (
        <Stack>
            <Text size="sm">
                <Trans>
                    Two-factor authentication protects your account with a second step after your password: a code from an authenticator app
                    or a passkey. You only need one of them to log in.
                </Trans>
            </Text>
            <Group>
                <Text fw={500}>
                    <Trans>Status:</Trans>
                </Text>
                {mfaEnabled ? (
                    <Badge color="green">
                        <Trans>Enabled</Trans>
                    </Badge>
                ) : (
                    <Badge color="gray">
                        <Trans>Disabled</Trans>
                    </Badge>
                )}
            </Group>

            <Divider />

            <Group gap="xs">
                <TbDeviceMobile size={20} />
                <Title order={4}>
                    <Trans>Authenticator app</Trans>
                </Title>
            </Group>
            {status.result.totpEnabled && (
                <Group>
                    <Badge color="green">
                        <Trans>Enabled</Trans>
                    </Badge>
                    <Button variant="outline" color="red" size="xs" onClick={() => setDisableTotpOpened(true)}>
                        <Trans>Disable</Trans>
                    </Button>
                </Group>
            )}
            {!status.result.totpEnabled && !totpSetup && (
                <Box>
                    {startTotpSetup.error && <Alert messages={errorToStrings(startTotpSetup.error)} />}
                    <Button onClick={async () => await startTotpSetup.execute()} loading={startTotpSetup.loading}>
                        <Trans>Set up an authenticator app</Trans>
                    </Button>
                </Box>
            )}
            {!status.result.totpEnabled && totpSetup && (
                <TotpSetup
                    setup={totpSetup}
                    onCancel={() => setTotpSetup(undefined)}
                    onEnabled={async () => {
                        setTotpSetup(undefined)
                        await status.execute()
                    }}
                />
            )}

            <Divider />

            <Group gap="xs">
                <TbKey size={20} />
                <Title order={4}>
                    <Trans>Passkeys</Trans>
                </Title>
            </Group>
            <Text size="sm" c="dimmed">
                <Trans>
                    Use your phone, computer (fingerprint, face, PIN) or a security key. Passkeys only work for the address (domain)
                    CommaFeed was opened with when they were added.
                </Trans>
            </Text>
            {status.result.passkeys.length > 0 && (
                <Table>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>
                                <Trans>Name</Trans>
                            </Table.Th>
                            <Table.Th>
                                <Trans>Added</Trans>
                            </Table.Th>
                            <Table.Th>
                                <Trans>Last used</Trans>
                            </Table.Th>
                            <Table.Th />
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                        {status.result.passkeys.map(passkey => (
                            <Table.Tr key={passkey.id}>
                                <Table.Td>{passkey.name}</Table.Td>
                                <Table.Td>
                                    <RelativeDate date={passkey.created} />
                                </Table.Td>
                                <Table.Td>{passkey.lastUsed ? <RelativeDate date={passkey.lastUsed} /> : <Trans>Never</Trans>}</Table.Td>
                                <Table.Td>
                                    <Tooltip label={<Trans>Delete</Trans>}>
                                        <ActionIcon variant="subtle" color="red" onClick={() => setPasskeyToDelete(passkey.id)}>
                                            <TbTrash size={16} />
                                        </ActionIcon>
                                    </Tooltip>
                                </Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            )}
            {passkeysSupported() ? (
                <form
                    onSubmit={async e => {
                        e.preventDefault()
                        await addPasskey.execute()
                    }}
                >
                    <Stack gap="xs">
                        {passkeyError.length > 0 && <Alert messages={passkeyError} />}
                        <Group align="flex-end">
                            <TextInput
                                label={<Trans>Name of the new passkey</Trans>}
                                placeholder={_(msg`e.g. My phone`)}
                                value={passkeyName}
                                onChange={e => setPasskeyName(e.currentTarget.value)}
                                maxLength={128}
                                required
                            />
                            <Button type="submit" leftSection={<TbPlus size={16} />} loading={addPasskey.loading}>
                                <Trans>Add a passkey</Trans>
                            </Button>
                        </Group>
                    </Stack>
                </form>
            ) : (
                <Alert
                    level="warning"
                    messages={[_(msg`Passkeys are only available when CommaFeed is accessed over HTTPS (or http://localhost).`)]}
                />
            )}

            <Divider />

            <Text size="sm" c="dimmed">
                <Trans>
                    Lost access to your authenticator app and passkeys? On the login page, choose "Lost access to your authenticator?" to
                    get a reset code. The code is written in the server logs (e.g. docker logs), so the administrator of the server has to
                    give it to you.
                </Trans>
            </Text>

            <PasswordConfirmationModal
                opened={disableTotpOpened}
                title={<Trans>Disable the authenticator app</Trans>}
                onClose={() => setDisableTotpOpened(false)}
                onConfirm={async password => {
                    await client.mfa.disableTotp(password)
                    await status.execute()
                }}
            />
            <PasswordConfirmationModal
                opened={passkeyToDelete !== undefined}
                title={<Trans>Delete the passkey</Trans>}
                onClose={() => setPasskeyToDelete(undefined)}
                onConfirm={async password => {
                    if (passkeyToDelete === undefined) return
                    await client.mfa.deletePasskey(passkeyToDelete, password)
                    await status.execute()
                }}
            />
        </Stack>
    )
}
