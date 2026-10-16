// titler-latin — the runtime and the 17-language model, ready to use.
//
//     val titler = TitlerLatin.load()
//     titler.title("Peux-tu m'expliquer la photosynthèse chez les plantes ?")
//
// English, French, Spanish, German, Portuguese, Italian, Dutch, Polish, Swedish,
// Danish, Finnish, Czech, Romanian, Norwegian, Hungarian, Croatian, Lithuanian.
// 133 KB, int3, version 1.2.0 of the weights.

package dev.apartejs.titler.latin

import dev.apartejs.titler.Titler
import dev.apartejs.titler.TitlerException

public object TitlerLatin {
    /** The name of the bundled model file. */
    public const val MODEL_FILE: String = "titler-v1.2-latin-int3.bin"

    // An absolute path: it still resolves when R8 renames or moves this class.
    private const val RESOURCE = "/dev/apartejs/titler/latin/$MODEL_FILE"

    /**
     * Load the bundled 17-language model. Load it once and keep the [Titler]:
     * it is immutable and safe to share across threads.
     */
    @JvmStatic
    public fun load(): Titler {
        val stream = TitlerLatin::class.java.getResourceAsStream(RESOURCE)
            ?: throw TitlerException("the bundled model $MODEL_FILE is missing")
        return stream.use { Titler.load(it) }
    }
}
