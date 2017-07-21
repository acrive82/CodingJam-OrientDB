package it.codingjam.orientdb

import groovy.json.JsonSlurperClassic
import it.codingjam.orientdb.model.Post

class PostSlurper {


    def getPost(url) {

        def listArticles = new ArrayList<Post>()

        def http = new URL(url).openConnection()
        def jsonslurper = new JsonSlurperClassic()

        def response = jsonslurper.parse(http.inputStream.newReader())
        response.posts.each {
            listArticles.add(new Post(it))
        }

        listArticles
    }


}
