package db

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import java.util.Locale

class StableNamingStrategy : PropertyNamingStrategy() {
    override fun nameForGetterMethod(config: MapperConfig<*>?, method: AnnotatedMethod?, defaultName: String): String {
        val methodName = method?.name ?: return defaultName

        return if (methodName.startsWith("is") && methodName.length > 2) {
            // Check if this is for a property that already starts with "is"
            val fieldName = methodName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val hasMatchingField = method.declaringClass?.declaredFields?.any { it.name == fieldName } == true

            if (hasMatchingField) {
                // Property already starts with "is" - keep full name
                methodName
            } else {
                // Property doesn't start with "is" - use default behavior
                defaultName
            }
        } else {
            defaultName
        }
    }
}
