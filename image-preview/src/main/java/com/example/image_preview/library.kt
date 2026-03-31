import okhttp3.OkHttpClient
import com.example.image_preview.bridge.ConsentInterceptor

object NetworkClient {

    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                ConsentInterceptor(
                    purpose = "Analytics",
                    dataCategory = "Location"
                )
            )
            .build()
    }
}

