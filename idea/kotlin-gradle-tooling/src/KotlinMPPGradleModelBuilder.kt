/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.jetbrains.plugins.gradle.model.AbstractExternalDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder
import java.io.File
import java.lang.Exception

class KotlinMPPGradleModelBuilder : ModelBuilderService {
    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder
            .create(project, e, "Gradle import errors")
            .withDescription("Unable to build Kotlin project configuration")
    }

    override fun canBuild(modelName: String?): Boolean {
        return modelName == KotlinMPPGradleModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): Any? {
        val dependencyResolver = DependencyResolverImpl(
            project,
            false,
            false,
            true,
            SourceSetCachedFinder(project)
        )
        val targetsWithGradle = buildTargets(project) ?: return null
        val sourceSets = buildSourceSets(targetsWithGradle, dependencyResolver, project) ?: return null
        val sourceSetMap = sourceSets.map { it.name to it }.toMap()
        buildCompilations(targetsWithGradle, sourceSetMap, dependencyResolver, project)
        val targets = targetsWithGradle.map { it.first }
        computeSourceSetsDeferredInfo(sourceSets, targets)
        val coroutinesState = getCoroutinesState(project)
        return KotlinMPPGradleModelImpl(sourceSetMap, targets, ExtraFeaturesImpl(coroutinesState))
    }

    private fun getCoroutinesState(project: Project): String? {
        val kotlinExt = project.extensions.findByName("kotlin") ?: return null
        val getExperimental = kotlinExt.javaClass.getMethodOrNull("getExperimental") ?: return null
        val experimentalExt = getExperimental(kotlinExt) ?: return null
        val getCoroutines = experimentalExt.javaClass.getMethodOrNull("getCoroutines") ?: return null
        return getCoroutines(experimentalExt) as? String
    }

    private fun buildSourceSets(
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        dependencyResolver: DependencyResolver,
        project: Project
    ): Collection<KotlinSourceSetImpl>? {
        val kotlinExt = project.extensions.findByName("kotlin") ?: return null
        val getSourceSets = kotlinExt.javaClass.getMethodOrNull("getSourceSets") ?: return null
        @Suppress("UNCHECKED_CAST")
        val sourceSets =
            (getSourceSets(kotlinExt) as? NamedDomainObjectContainer<Named>)?.asMap?.values ?: emptyList<Named>()
        return sourceSets.mapNotNull { buildSourceSet(it, targetsWithGradle, dependencyResolver, project) }
    }

    private fun buildSourceSet(
        gradleSourceSet: Named,
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        dependencyResolver: DependencyResolver,
        project: Project
    ): KotlinSourceSetImpl? {
        val sourceSetClass = gradleSourceSet.javaClass
        val getLanguageSettings = sourceSetClass.getMethodOrNull("getLanguageSettings") ?: return null
        val getSourceDirSet = sourceSetClass.getMethodOrNull("getKotlin") ?: return null
        val getResourceDirSet = sourceSetClass.getMethodOrNull("getResources") ?: return null
        val getDependsOn = sourceSetClass.getMethodOrNull("getDependsOn") ?: return null
        val languageSettings = getLanguageSettings(gradleSourceSet)?.let { buildLanguageSettings(it) } ?: return null
        val sourceDirs = (getSourceDirSet(gradleSourceSet) as? SourceDirectorySet)?.srcDirs ?: emptySet()
        val resourceDirs = (getResourceDirSet(gradleSourceSet) as? SourceDirectorySet)?.srcDirs ?: emptySet()
        val dependencies = buildSourceSetDependencies(gradleSourceSet, targetsWithGradle, dependencyResolver, project)
        @Suppress("UNCHECKED_CAST")
        val dependsOnSourceSets = (getDependsOn(gradleSourceSet) as? Set<Named>)?.mapTo(LinkedHashSet()) { it.name } ?: emptySet<String>()
        return KotlinSourceSetImpl(gradleSourceSet.name, languageSettings, sourceDirs, resourceDirs, dependencies, dependsOnSourceSets)
    }

    private fun buildLanguageSettings(gradleLanguageSettings: Any): KotlinLanguageSettings? {
        val languageSettingsClass = gradleLanguageSettings.javaClass
        val getLanguageVersion = languageSettingsClass.getMethodOrNull("getLanguageVersion") ?: return null
        val getApiVersion = languageSettingsClass.getMethodOrNull("getApiVersion") ?: return null
        val getProgressiveMode = languageSettingsClass.getMethodOrNull("getProgressiveMode") ?: return null
        val getEnabledLanguageFeatures = languageSettingsClass.getMethodOrNull("getEnabledLanguageFeatures") ?: return null
        @Suppress("UNCHECKED_CAST")
        return KotlinLanguageSettingsImpl(
            getLanguageVersion(gradleLanguageSettings) as? String,
            getApiVersion(gradleLanguageSettings) as? String,
            getProgressiveMode(gradleLanguageSettings) as? Boolean ?: false,
            getEnabledLanguageFeatures(gradleLanguageSettings) as? Set<String> ?: emptySet()
        )
    }

    private fun buildDependencies(
        dependencyHolder: Any,
        dependencyResolver: DependencyResolver,
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        configurationNameAccessor: String,
        scope: String,
        project: Project
    ): Collection<KotlinDependency> {
        val dependencyHolderClass = dependencyHolder.javaClass
        val getConfigurationName = dependencyHolderClass.getMethodOrNull(configurationNameAccessor) ?: return emptyList()
        val configurationName = getConfigurationName(dependencyHolder) as? String ?: return emptyList()
        val configuration = project.configurations.findByName(configurationName) ?: return emptyList()
        if (!configuration.isCanBeResolved) return emptyList()
        val dependenciesByProjectPath = configuration
            .resolvedConfiguration
            .lenientConfiguration
            .firstLevelModuleDependencies
            .mapNotNull { dependency ->
                val artifact = dependency.moduleArtifacts.firstOrNull {
                    it.id.componentIdentifier is ProjectComponentIdentifier
                } ?: return@mapNotNull null
                dependency to artifact
            }
            .groupBy { (it.second.id.componentIdentifier as ProjectComponentIdentifier).projectPath }
        return dependencyResolver.resolveDependencies(configuration)
            .apply {
                forEach<ExternalDependency?> { (it as? AbstractExternalDependency)?.scope = scope }
            }
            .map { dependency ->
                if (dependency !is ExternalProjectDependency
                    || dependency.configurationName != Dependency.DEFAULT_CONFIGURATION) return@map dependency
                val artifacts = dependenciesByProjectPath[dependency.projectPath] ?: return@map dependency
                val artifactConfiguration = artifacts.mapTo(LinkedHashSet()) {
                    it.first.configuration
                }.singleOrNull() ?: return@map dependency
                val taskGetterName = when (scope) {
                    "COMPILE" -> "getApiElementsConfigurationName"
                    "RUNTIME" -> "getRuntimeElementsConfigurationName"
                    else -> return@map dependency
                }
                val kotlinTarget = targetsWithGradle.firstOrNull { (_, gradleTarget) ->
                    val getter = gradleTarget.javaClass.getMethodOrNull(taskGetterName) ?: return@firstOrNull false
                    getter(gradleTarget) == artifactConfiguration
                }?.first ?: return@map dependency
                DefaultExternalProjectDependency(dependency).apply {
                    this.classifier = kotlinTarget.disambiguationClassifier
                }
            }
    }

    private fun buildTargets(project: Project): Collection<Pair<KotlinTargetImpl, Named>>? {
        val kotlinExt = project.extensions.findByName("kotlin") ?: return null
        val getTargets = kotlinExt.javaClass.getMethodOrNull("getTargets") ?: return null
        @Suppress("UNCHECKED_CAST")
        val targets = (getTargets.invoke(kotlinExt) as? NamedDomainObjectContainer<Named>)?.asMap?.values ?: emptyList<Named>()
        return targets.mapNotNull { target -> buildTarget(target, project)?.let { it to target } }
    }

    private fun buildTarget(gradleTarget: Named, project: Project): KotlinTargetImpl? {
        val targetClass = gradleTarget.javaClass
        val getPlatformType = targetClass.getMethodOrNull("getPlatformType") ?: return null
        val getDisambiguationClassifier = targetClass.getMethodOrNull("getDisambiguationClassifier") ?: return null
        val platformId = (getPlatformType.invoke(gradleTarget) as? Named)?.name ?: return null
        val platform = KotlinPlatform.byId(platformId) ?: return null
        val disambiguationClassifier = getDisambiguationClassifier(gradleTarget) as? String
        @Suppress("UNCHECKED_CAST")
        val jar = buildTargetJar(gradleTarget, project)
        return KotlinTargetImpl(gradleTarget.name, disambiguationClassifier, platform, jar)
    }

    private fun buildCompilations(
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        sourceSetMap: Map<String, KotlinSourceSet>,
        dependencyResolver: DependencyResolver,
        project: Project
    ) {
        for ((kotlinTarget, gradleTarget) in targetsWithGradle) {
            val targetClass = gradleTarget.javaClass
            val getCompilations = targetClass.getMethodOrNull("getCompilations") ?: continue
            @Suppress("UNCHECKED_CAST")
            val gradleCompilations =
                (getCompilations.invoke(gradleTarget) as? NamedDomainObjectContainer<Named>)?.asMap?.values ?: emptyList<Named>()
            val compilations = gradleCompilations.mapNotNull {
                buildCompilation(it, kotlinTarget.disambiguationClassifier, sourceSetMap, targetsWithGradle, dependencyResolver, project)
            }
            compilations.forEach { it.target = kotlinTarget }
            kotlinTarget.compilations = compilations
        }
    }

    private fun buildTargetJar(gradleTarget: Named, project: Project): KotlinTargetJar? {
        val targetClass = gradleTarget.javaClass
        val getArtifactsTaskName = targetClass.getMethodOrNull("getArtifactsTaskName") ?: return null
        val artifactsTaskName = getArtifactsTaskName(gradleTarget) as? String ?: return null
        val jarTask = project.tasks.findByName(artifactsTaskName) ?: return null
        val jarTaskClass = jarTask.javaClass
        val getArchivePath = jarTaskClass.getMethodOrNull("getArchivePath")
        val archiveFile = getArchivePath?.invoke(jarTask) as? File?
        return KotlinTargetJarImpl(archiveFile)
    }

    private fun buildCompilation(
        gradleCompilation: Named,
        classifier: String?,
        sourceSetMap: Map<String, KotlinSourceSet>,
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        dependencyResolver: DependencyResolver,
        project: Project
    ): KotlinCompilationImpl? {
        val compilationClass = gradleCompilation.javaClass
        val getKotlinSourceSets = compilationClass.getMethodOrNull("getKotlinSourceSets") ?: return null
        @Suppress("UNCHECKED_CAST")
        val kotlinGradleSourceSets = (getKotlinSourceSets(gradleCompilation) as? Collection<Named>) ?: return null
        val kotlinSourceSets = kotlinGradleSourceSets.mapNotNull { sourceSetMap[it.name] }
        val getCompileKotlinTaskName = compilationClass.getMethodOrNull("getCompileKotlinTaskName") ?: return null
        @Suppress("UNCHECKED_CAST")
        val compileKotlinTaskName = (getCompileKotlinTaskName(gradleCompilation) as? String) ?: return null
        val compileKotlinTask = project.tasks.findByName(compileKotlinTaskName) ?: return null
        val output = buildCompilationOutput(gradleCompilation, compileKotlinTask) ?: return null
        val arguments = buildCompilationArguments(compileKotlinTask) ?: return null
        val dependencyClasspath = buildDependencyClasspath(compileKotlinTask)
        val dependencies = buildCompilationDependencies(gradleCompilation, classifier, sourceSetMap, targetsWithGradle, dependencyResolver, project)
        return KotlinCompilationImpl(gradleCompilation.name, kotlinSourceSets, dependencies, output, arguments, dependencyClasspath)
    }

    private fun buildCompilationDependencies(
        gradleCompilation: Named,
        classifier: String?,
        sourceSetMap: Map<String, KotlinSourceSet>,
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        dependencyResolver: DependencyResolver,
        project: Project
    ): Set<KotlinDependency> {
        return LinkedHashSet<KotlinDependency>().apply {
            this += buildDependencies(
                gradleCompilation,
                dependencyResolver,
                targetsWithGradle,
                "getCompileDependencyConfigurationName",
                "COMPILE",
                project
            )
            this += buildDependencies(
                gradleCompilation,
                dependencyResolver,
                targetsWithGradle,
                "getRuntimeDependencyConfigurationName",
                "RUNTIME",
                project
            )
            this += sourceSetMap[compilationFullName(gradleCompilation.name, classifier)]?.dependencies ?: emptySet()
        }
    }

    private fun buildSourceSetDependencies(
        gradleSourceSet: Named,
        targetsWithGradle: Collection<Pair<KotlinTargetImpl, Named>>,
        dependencyResolver: DependencyResolver,
        project: Project
    ): Set<KotlinDependency> {
        return LinkedHashSet<KotlinDependency>().apply {
            this += buildDependencies(
                gradleSourceSet,
                dependencyResolver,
                targetsWithGradle,
                "getApiMetadataConfigurationName",
                "COMPILE",
                project
            )
            this += buildDependencies(
                gradleSourceSet,
                dependencyResolver,
                targetsWithGradle,
                "getImplementationMetadataConfigurationName",
                "COMPILE",
                project
            )
            this += buildDependencies(
                gradleSourceSet,
                dependencyResolver,
                targetsWithGradle,
                "getCompileOnlyMetadataConfigurationName",
                "COMPILE",
                project
            )
            this += buildDependencies(
                gradleSourceSet,
                dependencyResolver,
                targetsWithGradle,
                "getRuntimeOnlyMetadataConfigurationName",
                "RUNTIME",
                project
            )
        }
    }

    private fun buildCompilationArguments(compileKotlinTask: Task): KotlinCompilationArguments? {
        val compileTaskClass = compileKotlinTask.javaClass
        val getCurrentArguments = compileTaskClass.getMethodOrNull("getSerializedCompilerArguments")
        val getDefaultArguments = compileTaskClass.getMethodOrNull("getDefaultSerializedCompilerArguments")

        //TODO: Hack for KotlinNativeCompile
        if (getCurrentArguments == null || getDefaultArguments == null) {
            return KotlinCompilationArgumentsImpl(emptyList(), emptyList())
        }
        @Suppress("UNCHECKED_CAST")
        val currentArguments = getCurrentArguments(compileKotlinTask) as? List<String> ?: return null
        @Suppress("UNCHECKED_CAST")
        val defaultArguments = getDefaultArguments(compileKotlinTask) as? List<String> ?: return null
        return KotlinCompilationArgumentsImpl(defaultArguments, currentArguments)
    }

    private fun buildDependencyClasspath(compileKotlinTask: Task): List<String> {
        val abstractKotlinCompileClass =
            compileKotlinTask.javaClass.classLoader.loadClass(AbstractKotlinGradleModelBuilder.ABSTRACT_KOTLIN_COMPILE_CLASS)
        val getCompileClasspath =
            abstractKotlinCompileClass.getDeclaredMethodOrNull("getCompileClasspath") ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (getCompileClasspath(compileKotlinTask) as? Collection<File>)?.map { it.path } ?: emptyList()
    }

    private fun buildCompilationOutput(
        gradleCompilation: Named,
        compileKotlinTask: Task
    ): KotlinCompilationOutput? {
        val compilationClass = gradleCompilation.javaClass
        val getOutput = compilationClass.getMethodOrNull("getOutput") ?: return null
        val gradleOutput = getOutput(gradleCompilation) ?: return null
        val gradleOutputClass = gradleOutput.javaClass
        val getClassesDirs = gradleOutputClass.getMethodOrNull("getClassesDirs") ?: return null
        val getResourcesDir = gradleOutputClass.getMethodOrNull("getResourcesDir") ?: return null
        val compileKotlinTaskClass = compileKotlinTask.javaClass
        val getDestinationDir = compileKotlinTaskClass.getMethodOrNull("getDestinationDir")
        val getOutputFile = compileKotlinTaskClass.getMethodOrNull("getOutputFile")
        val classesDirs = getClassesDirs(gradleOutput) as? FileCollection ?: return null
        val resourcesDir = getResourcesDir(gradleOutput) as? File ?: return null
        val destinationDir =
            getDestinationDir?.invoke(compileKotlinTask) as? File
            //TODO: Hack for KotlinNativeCompile
                ?: (getOutputFile?.invoke(compileKotlinTask) as? Property<File>)?.orNull?.parentFile
                ?: return null
        return KotlinCompilationOutputImpl(classesDirs.files, destinationDir, resourcesDir)
    }

    private fun computeSourceSetsDeferredInfo(
        sourceSets: Collection<KotlinSourceSetImpl>,
        targets: Collection<KotlinTarget>
    ) {
        val sourceSetToCompilations = LinkedHashMap<KotlinSourceSet, MutableSet<KotlinCompilation>>()
        for (target in targets) {
            for (compilation in target.compilations) {
                for (sourceSet in compilation.sourceSets) {
                    sourceSetToCompilations.getOrPut(sourceSet) { LinkedHashSet() } += compilation
                }
            }
        }
        for (sourceSet in sourceSets) {
            val name = sourceSet.name
            if (name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME) {
                sourceSet.platform = KotlinPlatform.COMMON
                sourceSet.isTestModule = false
                continue
            }
            if (name == KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME) {
                sourceSet.platform = KotlinPlatform.COMMON
                sourceSet.isTestModule = true
                continue
            }

            val compilations = sourceSetToCompilations[sourceSet]
            if (compilations != null) {
                sourceSet.platform = compilations.map { it.platform }.distinct().singleOrNull() ?: KotlinPlatform.COMMON
                sourceSet.isTestModule = compilations.all { it.isTestModule }
            } else {
                // TODO: change me after design about it
                sourceSet.isTestModule = "Test" in sourceSet.name
            }
        }
    }
}