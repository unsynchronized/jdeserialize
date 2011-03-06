import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

@Documented
@Retention(value=RetentionPolicy.RUNTIME)
public @interface blobann {
    int id();
    String sfoo();
    String sdefault() default "[unknown]";
    Class cl();
}
