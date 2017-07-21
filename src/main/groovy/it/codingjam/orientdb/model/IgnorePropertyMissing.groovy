package it.codingjam.orientdb.model

trait IgnorePropertyMissing {
    def propertyMissing(String name, value) {
    }
}