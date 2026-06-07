package karaoke.app

import android.app.Application

class KaraokeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
