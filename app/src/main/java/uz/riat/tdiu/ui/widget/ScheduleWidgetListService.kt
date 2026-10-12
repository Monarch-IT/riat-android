package uz.riat.tdiu.ui.widget

import android.content.Intent
import android.widget.RemoteViewsService
class ScheduleWidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetListFactory(applicationContext, intent)
    }
}
