package de.jonasbroeckmann.kzip.implementation.util

internal class ConcatenatedList<out T>(
    private val lists: List<List<T>>
) : AbstractList<T>() {
    constructor(vararg lists: List<T>) : this(listOf(*lists))

    override val size: Int get() = lists.sumOf { it.size }

    private fun <R> listAt(index: Int, endInclusive: Boolean = false, block: List<T>.(Int) -> R): R {
        if (index < 0) throw IndexOutOfBoundsException("Index: $index")
        var i = index
        lists.forEach { list ->
            if (i < list.size || (endInclusive && i == list.size)) return list.block(i)
            i -= list.size
        }
        throw IndexOutOfBoundsException("Index: $index, Size: ${index - i}")
    }

    override fun get(index: Int) = listAt(index) { get(it) }

    override fun contains(element: @UnsafeVariance T) = lists.any { element in it }

    override fun containsAll(elements: Collection<@UnsafeVariance T>) = elements.all { it in this }
}