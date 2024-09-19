package com.cwsoft.flydi;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@ToString
@Getter
public class BeanScannerConfig {
    private final String system;
    private final List<String> packagesToInclude;
    private final List<String> packagesToExclude;

    @Builder
    public BeanScannerConfig(String system, @Singular("includePackage") List<String> packagesToInclude, @Singular("excludePackage") List<String> packagesToExclude) {
        if (packagesToInclude == null || packagesToInclude.isEmpty())
            throw new IllegalArgumentException("packagesToInclude cannot be null or empty");
        this.system = (system == null || system.isBlank()) ? "" : system;
        this.packagesToInclude = packagesToInclude;
        this.packagesToExclude = (packagesToExclude == null) ? Collections.emptyList() : packagesToExclude;
    }

    public boolean isInScannedPackages(Object clazz) {
        var className = clazz.getClass().getName();
        for (String pkg : packagesToInclude) {
            if (className.startsWith(pkg))
                return true;
        }
        return false;
    }

}
