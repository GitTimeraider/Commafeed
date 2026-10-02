import { Trans } from "@lingui/react/macro"
import { Container, Text, Title } from "@mantine/core"
import { PageTitle } from "@/pages/PageTitle"

export function AccessRestrictedPage() {
    return (
        <Container size="xs">
            <PageTitle />
            <Title order={2} ta="center" mb="md">
                <Trans>Not available from your network</Trans>
            </Title>
            <Text ta="center" c="dimmed">
                <Trans>This page can only be opened from an allowed network.</Trans>
            </Text>
        </Container>
    )
}
