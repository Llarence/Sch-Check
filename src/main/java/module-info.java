module me.llarence.schcheck {
    requires javafx.base;
    requires javafx.controls;

    requires kotlinx.serialization.json;

    requires com.calendarfx.view;

    requires kotlinx.coroutines.javafx;
    requires kotlinx.coroutines.core;

    requires okhttp3;
    requires org.slf4j;

    exports me.llarence.schcheck;
}