package es.uniovi.federico.gijonsmartparking.data

object FavoriteMerge {

    fun merge(remote: Collection<Long>, pendingAdds: Set<Long>, pendingRemoves: Set<Long>): List<Long> =
        (remote.toSet() + pendingAdds - pendingRemoves).toList()
}
