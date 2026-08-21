package ua.ukrainedrones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.random.Random

/** Resolves the walk-time figure icons. The adult is randomly a man or a woman so the UI
 *  feels inclusive; when [withKids] is on the child is drawn to the right of the adult.
 *  The adult is always on the left. */
@Composable
fun walkIconPair(withKids: Boolean): Pair<Int, Int?> {
    val man = remember { Random.nextBoolean() }
    val adult = if (man) R.drawable.ic_walk_man else R.drawable.ic_walk_woman
    val child = if (withKids) R.drawable.ic_walk_child else null
    return adult to child
}
