package sag.types

sealed trait CacheType

// Cache data meant to be send to warehouse
case object CacheWarehouse extends CacheType

// Cache data meant to be send to recorder
case object CacheRecorded extends CacheType

// Cache both types of data 
case object CacheBoth extends CacheType