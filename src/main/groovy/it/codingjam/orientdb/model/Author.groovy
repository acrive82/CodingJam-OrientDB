package it.codingjam.orientdb.model

import groovy.transform.ToString

@ToString
class Author implements IgnorePropertyMissing {
    int id
    String name
    String nickname
}
