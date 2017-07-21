package it.codingjam.orientdb.model

import groovy.transform.ToString
import it.codingjam.orientdb.model.Author
import it.codingjam.orientdb.model.Categories
import it.codingjam.orientdb.model.IgnorePropertyMissing
import it.codingjam.orientdb.model.Tags

@ToString(excludes = "content,url,type")
class Post implements IgnorePropertyMissing {

    int id
    String url
    String title
    String content
    String date
    List<Categories> categories
    List<Tags> tags
    Author author
}