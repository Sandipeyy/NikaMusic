package com.sandip.nikamusic.module.bridge

import com.sandip.nikamusic.module.AccountState
import com.sandip.nikamusic.module.DiagnosticStep
import com.sandip.nikamusic.module.FieldKind
import com.sandip.nikamusic.module.HttpRequest
import com.sandip.nikamusic.module.ItemListModule
import com.sandip.nikamusic.module.ItemProblem
import com.sandip.nikamusic.module.ItemsAdded
import com.sandip.nikamusic.module.LicenseServer
import com.sandip.nikamusic.module.MatchedTrack
import com.sandip.nikamusic.module.MetadataModule
import com.sandip.nikamusic.module.ModuleArt
import com.sandip.nikamusic.module.ModuleChoice
import com.sandip.nikamusic.module.ModuleFeature
import com.sandip.nikamusic.module.ModuleField
import com.sandip.nikamusic.module.ModuleFields
import com.sandip.nikamusic.module.ModuleHost
import com.sandip.nikamusic.module.ModuleInfo
import com.sandip.nikamusic.module.ModuleItem
import com.sandip.nikamusic.module.ResolveResult
import com.sandip.nikamusic.module.SourceModule
import com.sandip.nikamusic.module.SourceStream
import com.sandip.nikamusic.module.StreamDrm
import com.sandip.nikamusic.module.StreamFormat
import com.sandip.nikamusic.module.StreamOption
import com.sandip.nikamusic.module.TrackMetadata
import com.sandip.nikamusic.module.TrackQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

// a module the bridge test loads on its own class loader, it reports back only through nika's host
class FakeModule : SourceModule, ItemListModule {

    override val info = ModuleInfo("fake", "Fake", "1.0", "Answers the bridge test")

    override val itemsLabel = "Scripts"
    override val itemsAbout = ""

    override val choices = listOf(ModuleChoice("quality", "Stream", listOf(StreamOption("lossless", "Lossless", "FLAC"))))

    private lateinit var host: ModuleHost
    private val kept = ArrayList<ModuleItem>()

    override fun attach(host: ModuleHost) {
        this.host = host
        host.storage.set("folder", host.folder.path)
    }

    override fun accountState(): AccountState = AccountState.SignedIn("ready")

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult {
        if (track.title == "boom") throw IllegalStateException("the module broke")
        if (track.title == "slow") {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                host.log("slow search cancelled")
                throw e
            }
        }
        val answer = host.http(HttpRequest("https://fake.example/search?q=${track.title}"))
        host.log("search said ${answer.header("Content-Type")} ${answer.text}")
        return ResolveResult.Found(
            SourceStream(
                url = "https://cdn.example/espresso.mpd?q=${picks["quality"]}",
                format = StreamFormat.Dash,
                headers = mapOf("Referer" to "https://fake.example"),
                drm = StreamDrm.Widevine(LicenseServer { it.reversedArray() }, listOf(byteArrayOf(1, 2, 3))),
                sourceLabel = "Fake",
                qualityLabel = "FLAC · 24-bit · 96 kHz",
                matched = MatchedTrack("1", track.title, track.artists.joinToString(), null, 175_000, 0.9f),
                codec = "FLAC",
                sampleRateHz = 96_000,
            ),
        )
    }

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        report(DiagnosticStep("Find", null, "Looking…"))
        report(DiagnosticStep("Find", false, "Not there"))
        return ResolveResult.NotFound("Not there")
    }

    override val items: List<ModuleItem> get() = kept.toList()

    override suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded {
        val name = "$fileName:${bytes.size}"
        kept += ModuleItem(name, name, "")
        return ItemsAdded.Added(listOf(name))
    }

    override suspend fun addLink(link: String): ItemsAdded = ItemsAdded.Problem(ItemProblem.NotALink)

    override fun removeItem(id: String) {
        kept.removeAll { it.id == id }
    }
}

// a module built against an older api, which nika has to turn away
class OldModule : SourceModule {
    override val info = ModuleInfo("old", "Old", "1.0", "Too old", apiVersion = 2)
    override fun attach(host: ModuleHost) = Unit
    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = ResolveResult.NotFound("")
    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult = ResolveResult.NotFound("")
}

// a module with everything that came later: settings of its own, song facts, pictures and items that say whether they work
class FakeRichModule : MetadataModule, ModuleFields, ModuleArt, ItemListModule {
    override val info = ModuleInfo("rich", "Rich", "1.0", "Brings song info")
    override val adds = setOf(ModuleFeature.Metadata)
    override val icon = "https://rich.example/icon.png"
    override val banner = "https://rich.example/banner.webp"
    override val itemsLabel = "Sources"
    override val itemsAbout = ""

    private var values: Map<String, String> = emptyMap()

    override val fields = listOf(
        ModuleField("region", "Region", FieldKind.Select, listOf(StreamOption("us", "US", ""), StreamOption("jp", "Japan", "")), default = "us"),
        ModuleField("token", "Token", FieldKind.Text, hint = "Paste it"),
    )

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun attach(host: ModuleHost) = Unit

    // answers in the region it was last told, so the test sees the values arrived
    override suspend fun metadata(track: TrackQuery): TrackMetadata? =
        TrackMetadata(genres = listOf("Pop"), label = values["region"], writers = listOf("Amy Allen"), about = "Says hi")

    override val items = listOf(ModuleItem("a", "A", "", icon = "https://rich.example/a.png", working = false))

    override suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded = ItemsAdded.Problem(ItemProblem.NothingFound)

    override suspend fun addLink(link: String): ItemsAdded = ItemsAdded.Problem(ItemProblem.NotALink)

    override fun removeItem(id: String) = Unit
}

