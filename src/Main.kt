import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress

fun main() {
    // 1. Creamos el servidor web local en el puerto 8080
    val servidor = HttpServer.create(InetSocketAddress(8080), 0)

    // 2. Definimos la ruta principal
    servidor.createContext("/") { intercambio ->
        // Buscamos el archivo index.html dentro de la carpeta de recursos
        val archivoHtml = File("src/resources/index.html")

        if (archivoHtml.exists()) {
            val respuesta = archivoHtml.readBytes()

            intercambio.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            intercambio.sendResponseHeaders(200, respuesta.size.toLong())
            val os: OutputStream = intercambio.responseBody
            os.write(respuesta)
            os.close()
        } else {
            // Error por si el archivo no se encuentra en la ruta
            val error404 = "Error 404: No se encontró el archivo index.html en src/resources/".toByteArray()
            intercambio.sendResponseHeaders(404, error404.size.toLong())
            val os: OutputStream = intercambio.responseBody
            os.write(error404)
            os.close()
        }
    }

    // Arrancar el servidor
    servidor.executor = null
    servidor.start()

    println("🌍 ¡Servidor web para Dijkstra iniciado con éxito!")
    println("👉 Abre tu navegador e ingresa a: http://localhost:8080")
}