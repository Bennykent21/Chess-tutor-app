package com.chesstutor.app.data.maia

import android.content.res.AssetManager
import com.chesstutor.app.domain.bot.MaiaSource
import com.chesstutor.app.domain.bot.maia.MaiaNet
import com.chesstutor.app.domain.bot.maia.MaiaPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the bundled Maia networks (`assets/maia/maia-XXXX.bin`, tools/maia_net.py) on first
 * use and keeps the last few in memory (about 2.4 MB each once unpacked). A missing or broken
 * network gives null, and the bot falls back to its engine mode.
 */
class MaiaAssetLoader(private val assets: AssetManager, private val keep: Int = 3) : MaiaSource {
    private val lock = Mutex()
    private var slots: Map<String, Int>? = null
    private val nets = LinkedHashMap<Int, MaiaPolicy>()

    override suspend fun policyFor(net: Int): MaiaPolicy? = lock.withLock {
        nets[net]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            runCatching {
                val table = slots ?: assets.open("maia/maia_moves.tsv").use(MaiaPolicy::readSlots).also { slots = it }
                MaiaPolicy(assets.open("maia/maia-$net.bin").use(MaiaNet::read), table)
            }.getOrNull()
        }?.also { policy ->
            nets[net] = policy
            while (nets.size > keep) nets.remove(nets.keys.first())
        }
    }
}
