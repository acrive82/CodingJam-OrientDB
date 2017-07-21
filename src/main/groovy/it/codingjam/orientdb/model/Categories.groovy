package it.codingjam.orientdb.model

import groovy.transform.ToString

@ToString
class Categories implements IgnorePropertyMissing {
    int id
    String slug
}
