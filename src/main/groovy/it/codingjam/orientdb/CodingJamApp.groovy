package it.codingjam.orientdb

import com.orientechnologies.orient.core.db.ODatabasePool
import com.orientechnologies.orient.core.db.ODatabaseType
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OVertex
import groovy.util.logging.Slf4j

@Slf4j
class CodingJamApp {

    def static CODING_JAM_URL = "http://codingjam.it/?json=get_recent_posts&count=150"

    static void main(String[] args) {

        /* Download articoli tramite le API di Wordpress */
        def postSlurper = new PostSlurper()
        def posts = postSlurper.getPost(CODING_JAM_URL)

        log.info "Articoli size\t ${posts.size()}"
        log.info "Articoli\t ${posts}"

        /* Connessione con OrientDB */
        OrientDB orientDB = new OrientDB("remote:localhost", "root", "password", OrientDBConfig.defaultConfig())
        ODatabasePool pool = new ODatabasePool(orientDB, "codingJamDatabase", "root", "password")

        orientDB.createIfNotExists("codingJamDatabase", ODatabaseType.PLOCAL) ? log.info("Database Creato!") : log.info("Database esistente")
        // Se non esiste, viene creato il database codingJamDatabase

        ODatabaseDocument database = pool.acquire()

        def articleVertexClass = database.createClassIfNotExist("Article", OClass.VERTEX_CLASS_NAME)
        def categoryVertexClass = database.createClassIfNotExist("Category", OClass.VERTEX_CLASS_NAME)
        def tagVertexClass = database.createClassIfNotExist("Tag", OClass.VERTEX_CLASS_NAME)
        def authorVertexClass = database.createClassIfNotExist("Author", OClass.VERTEX_CLASS_NAME)

        def hasCategoryEdgeClass = database.createClassIfNotExist("hasCategory", OClass.EDGE_CLASS_NAME)
        def hasTagEdgeClass = database.createClassIfNotExist("hasTag", OClass.EDGE_CLASS_NAME)
        def isAuthorEdgeClass = database.createClassIfNotExist("isAuthor", OClass.EDGE_CLASS_NAME)

        /* Constraints */
        articleVertexClass.createProperty("url", OType.STRING)
        articleVertexClass.createProperty("content", OType.STRING)
        articleVertexClass.createProperty("date", OType.DATE)
        articleVertexClass.createProperty("title", OType.STRING)
        articleVertexClass.createProperty("w_id", OType.STRING)

        categoryVertexClass.createProperty("slug", OType.STRING)
        categoryVertexClass.createIndex("categoryIDX", OClass.INDEX_TYPE.UNIQUE, "slug")

        tagVertexClass.createProperty("slug", OType.STRING)
        tagVertexClass.createIndex("tagIDX", OClass.INDEX_TYPE.UNIQUE, "slug")

        authorVertexClass.createProperty("nickname", OType.STRING)
        authorVertexClass.createProperty("name", OType.STRING)
        authorVertexClass.createIndex("authorIDX", OClass.INDEX_TYPE.UNIQUE, "name", "nickname")

        database.begin()

        try {
            /* Popoliamo il db con gli articoli */
            posts.each {

                /* Creo un vertex di class Article (che abbiamo definito precedentemente) e lo popolo con i dati prelevati dal sito */
                OVertex articleVertex = database.newVertex("Article")
                articleVertex.setProperty("url", it.url)
                articleVertex.setProperty("content", it.content)
                articleVertex.setProperty("date", it.date)
                articleVertex.setProperty("title", it.title)
                articleVertex.setProperty("w_id", it.id)
                articleVertex.save()

                /* Occupiamoci delle categorie. N.B. Le categorie possono essere ripetute  */
                it.categories.each { category ->

                    def listVertx = findVertexByClassAndEqualProperties(database, "Category", ["slug": category.slug])

                    OVertex categoryVertex = listVertx ? listVertx.get(0).toElement() : null
                    if (!categoryVertex) {
                        categoryVertex = database.newVertex("Category")
                        categoryVertex.setProperty("slug", category.slug)
                        categoryVertex.save()
                    }
                    articleVertex.addEdge(categoryVertex, hasCategoryEdgeClass).save()
                    database.commit()
                }

                it.tags.each { tag ->
                    def listVertx = findVertexByClassAndEqualProperties(database, "Tag", ["slug": tag.slug])
                    OVertex tagVertex = listVertx ? listVertx.get(0).toElement() : null
                    if (!tagVertex) {
                        tagVertex = database.newVertex("Tag")
                        tagVertex.setProperty("slug", tag.slug)
                        tagVertex.save()
                    }
                    articleVertex.addEdge(tagVertex, hasTagEdgeClass).save()
                    database.commit()
                }

                def listAuthorVertx = findVertexByClassAndEqualProperties(database, "Author", ["name": it.author.name])

                OVertex authorVertex = listAuthorVertx ? listAuthorVertx.get(0).toElement() : null
                if (!authorVertex) {
                    authorVertex = database.newVertex("Author")
                    authorVertex.setProperty("name", it.author?.name)
                    authorVertex.setProperty("nickname", it.author?.nickname)
                    authorVertex.save()
                }
                articleVertex.addEdge(authorVertex, isAuthorEdgeClass).save()
                database.commit()

            }

        } catch (Exception ex) {
            ex.printStackTrace()
            log.error "Errore durante l'inserimento\t${ex.message}\tROLLBACK!"
            database.rollback()
        }


        database.close()
    }

    static List<OVertex> findVertexByClassAndEqualProperties(ODatabaseDocument database, String className, LinkedHashMap<String, String> properties) {
        def result = []
        def sqlString = "select * from ${className} where ${"${properties.collect { "${it.key} = \"${it.value}\"" }}".replaceAll("\\[|\\]", "")}"
        log.info("findVertexByClassAndEqualProperties:\t${sqlString}")
        result.addAll(database.query(sqlString, null))
        result
    }


}
