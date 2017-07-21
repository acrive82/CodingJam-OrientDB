package it.codingjam.orientdb.model

import groovy.transform.ToString

@ToString
class Tags implements IgnorePropertyMissing {
    int id
    String slug
}
