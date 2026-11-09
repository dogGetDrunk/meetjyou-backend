package com.dogGetDrunk.meetjyou.place.importer

/** An import run stopped before touching `place` (bad download, broken file) or while applying. */
class PlaceImportException(message: String) : RuntimeException(message)
