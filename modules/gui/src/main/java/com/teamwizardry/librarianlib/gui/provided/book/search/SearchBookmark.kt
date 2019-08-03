package com.teamwizardry.librarianlib.gui.provided.book.search

import com.teamwizardry.librarianlib.gui.provided.book.IBookGui
import com.teamwizardry.librarianlib.gui.provided.book.context.Bookmark
import com.teamwizardry.librarianlib.gui.provided.book.context.ComponentBookMark

/**
 * @author WireSegal
 * Created at 9:49 PM on 8/6/18.
 */
class SearchBookmark : Bookmark {

    var component: ComponentSearchBar? = null

    override fun createBookmarkComponent(book: IBookGui, bookmarkIndex: Int): ComponentBookMark {
        var c = component
        if (c?.parent == null) {
            c = ComponentSearchBar(book, bookmarkIndex, TFIDFSearch(book).textBoxConsumer(book) { SearchResults(book.book, it) })
            component = c
        } else
            c.parent?.remove(c)

        return c
    }

}