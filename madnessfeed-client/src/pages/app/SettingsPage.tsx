import { Trans } from "@lingui/react/macro"
import { Container, Tabs } from "@mantine/core"
import { TbBell, TbCode, TbPhoto, TbShieldLock, TbUser, TbWorld } from "react-icons/tb"
import { CustomCodeSettings } from "@/components/settings/CustomCodeSettings"
import { DisplaySettings } from "@/components/settings/DisplaySettings"
import { ProfileSettings } from "@/components/settings/ProfileSettings"
import { PublicPageSettings } from "@/components/settings/PublicPageSettings"
import { PushNotificationSettings } from "@/components/settings/PushNotificationSettings"
import { SecuritySettings } from "@/components/settings/SecuritySettings"

export function SettingsPage() {
    return (
        <Container size="sm" px={0}>
            <Tabs defaultValue="display" keepMounted={false}>
                <Tabs.List>
                    <Tabs.Tab value="display" leftSection={<TbPhoto size={16} />}>
                        <Trans>Display</Trans>
                    </Tabs.Tab>
                    <Tabs.Tab value="push-notifications" leftSection={<TbBell size={16} />}>
                        <Trans>Push notifications</Trans>
                    </Tabs.Tab>
                    <Tabs.Tab value="public-page" leftSection={<TbWorld size={16} />}>
                        <Trans>Public page</Trans>
                    </Tabs.Tab>
                    <Tabs.Tab value="customCode" leftSection={<TbCode size={16} />}>
                        <Trans>Custom code</Trans>
                    </Tabs.Tab>
                    <Tabs.Tab value="profile" leftSection={<TbUser size={16} />}>
                        <Trans>Profile</Trans>
                    </Tabs.Tab>
                    <Tabs.Tab value="security" leftSection={<TbShieldLock size={16} />}>
                        <Trans>Security</Trans>
                    </Tabs.Tab>
                </Tabs.List>

                <Tabs.Panel value="display" pt="xl">
                    <DisplaySettings />
                </Tabs.Panel>

                <Tabs.Panel value="push-notifications" pt="xl">
                    <PushNotificationSettings />
                </Tabs.Panel>

                <Tabs.Panel value="public-page" pt="xl">
                    <PublicPageSettings />
                </Tabs.Panel>

                <Tabs.Panel value="customCode" pt="xl">
                    <CustomCodeSettings />
                </Tabs.Panel>

                <Tabs.Panel value="profile" pt="xl">
                    <ProfileSettings />
                </Tabs.Panel>

                <Tabs.Panel value="security" pt="xl">
                    <SecuritySettings />
                </Tabs.Panel>
            </Tabs>
        </Container>
    )
}
