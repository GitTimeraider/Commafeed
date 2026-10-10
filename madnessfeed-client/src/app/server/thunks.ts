import { createAppAsyncThunk } from "@/app/async-thunk"
import { client } from "@/app/client"
import { prefetchedOr, takePrefetchedServerInfos } from "@/app/prefetch"

export const reloadServerInfos = createAppAsyncThunk("server/infos", async () =>
    prefetchedOr(takePrefetchedServerInfos(), async () => await client.server.getServerInfos().then(r => r.data))
)
