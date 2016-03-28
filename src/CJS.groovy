@Grab("com.orientechnologies:orientdb-core:2.1.12")
@Grab("com.orientechnologies:orientdb-client:2.1.12")
@Grab("com.orientechnologies:orientdb-graphdb:2.1.12")
@Grab("com.orientechnologies:orientdb-object:2.1.12")
@Grab('com.orientechnologies:orient-commons:1.7.10')
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import groovy.json.JsonSlurperClassic
import groovy.transform.ToString

/**
 * Created by Luigi Candita on 26/03/16.
 */


trait IgnorePropertyMissing {
    def propertyMissing(String name, value) {
    }
}

@ToString(excludes = "content,url,type")
class Article implements IgnorePropertyMissing {
    int id
    String url
    String title
    String content
    String date
    List<Categories> categories
    List<Tags> tags
    Author author
}

@ToString
class Categories implements IgnorePropertyMissing {
    int id
    String slug
}

@ToString
class Author implements IgnorePropertyMissing {
    int id
    String name
    String nickname
}

@ToString
class Tags implements IgnorePropertyMissing {
    int id
    String slug
}

class CodingJamSlurper {
    def cj_url = "http://codingjam.it/?json=get_recent_posts&count=300"
    def jsonslurper = new JsonSlurperClassic()

    def query() {
        def http = new URL(cj_url).openConnection()
        jsonslurper.parse(http.inputStream.newReader())
    }
}

/* Let's go */
def codingJamSlurper = new CodingJamSlurper()
def response = codingJamSlurper.query()
def listArticles = new ArrayList<Article>()
response.posts.each {
    listArticles.add(new Article(it))
}


/* Talk with orientdb */
OrientGraphFactory factory = new OrientGraphFactory("remote:localhost/codingjam", "root", "password").setupPool(1, 10)

OrientGraph txForType = factory.getTx()

/* Creiamo le class orientdb da utilizzare */
txForType.getVertexType("Article") ?: txForType.createVertexType("Article")
txForType.getVertexType("Category") ?: txForType.createVertexType("Category")
txForType.getVertexType("Tag") ?: txForType.createVertexType("Tag")
txForType.getVertexType("Author") ?: txForType.createVertexType("Author")

txForType.commit()

OrientGraph graph = factory.getTx()
try {

    listArticles.each {
        Vertex vertex = graph.addVertex("class:Article")
        vertex.setProperty("url", it.url)
        vertex.setProperty("content", it.content)
        vertex.setProperty("date", it.date)
        vertex.setProperty("title", it.title)
        vertex.setProperty("w_id", it.id)

        it.categories.each {
            def vertexCategory
            def vertexCategoryList = findVertexByClassAndEqualProperties(graph, "Category", [slug: it.slug])
            if (vertexCategoryList) {
                vertexCategory = vertexCategoryList.get(0)
            } else {
                vertexCategory = graph.addVertex("class:Category")
                vertexCategory.setProperty("slug", it.slug)
                graph.commit()

            }

            graph.addEdge("class:HasCategory", vertex, vertexCategory, null)
        }

        it.tags.each {
            Vertex vertexTag
            def vertexTagList = findVertexByClassAndEqualProperties(graph, "Tag", [slug: it.slug])
            if (vertexTagList) {
                vertexTag = vertexTagList.get(0)
            } else {
                vertexTag = graph.addVertex("class:Tag")
                vertexTag.setProperty("slug", it.slug)
                graph.commit()
            }

            graph.addEdge("class:HasTag", vertex, vertexTag, null)
        }

        Vertex vertexAuthor
        def vertexAuthorList = findVertexByClassAndEqualProperties(graph, "Author", [nickname: it.author.nickname])
        if (vertexAuthorList) {
            vertexAuthor = vertexAuthorList.get(0)
        } else {
            vertexAuthor = graph.addVertex("class:Author")
            vertexAuthor.setProperty("nickname", it.author.nickname)
            vertexAuthor.setProperty("name", it.author.name)
            graph.commit()
        }

        graph.addEdge("class:isAuthor", vertex, vertexAuthor, null)


    }

    graph.commit()

} catch (Exception e) {
    graph.rollback()
    println e.getMessage()
} finally {
    graph.shutdown()
}


def List<Vertex> findVertexByClassAndEqualProperties(OrientGraph txDb, String className, Map map_prop) {
    def result = []
    def sqlString = "select * from ${className} where ${"${map_prop.collect { "${it.key} = \"${it.value}\"" }}".replaceAll("\\[|\\]", "")}"
    result.addAll(txDb.command(new OCommandSQL(sqlString)).execute())
    result
}
