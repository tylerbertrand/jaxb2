package com.gradleup.jaxb.tasks

import com.gradleup.jaxb.Jaxb2Plugin
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import static org.gradle.api.logging.Logging.getLogger

/**
 * Task that does the actual generation stuff. Declares the Ant task and then runs it for all
 * configured {@link XjcTaskConfig} objects.
 *
 * xjc https://jaxb.java.net/2.2.4/docs/xjcTask.html
 * depends/produces is used for incremental compilation
 *
 * @author holgerstolzenberg
 * @since 1.0.0
 */
class GenerateJaxb2Classes extends DefaultTask {
  private static final Logger LOG = getLogger(GenerateJaxb2Classes.class)

  @Input
  XjcTaskConfig theConfig

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  final RegularFileProperty schemaFile = project.objects.fileProperty()

  @Input
  final Property<String> basePackage = project.objects.property(String)

  @Input
  final Property<String> encoding = project.objects.property(String)

  @Input
  final Property<Boolean> extension = project.objects.property(Boolean)

  @Input
  final Property<String> additionalArgs = project.objects.property(String)

  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  final RegularFileProperty catalogFile = project.objects.fileProperty()

  @InputFiles
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  final ConfigurableFileCollection bindingFiles = project.objects.fileCollection()

  @OutputDirectory
  final DirectoryProperty generatedSourcesDirectory = project.objects.directoryProperty()

  GenerateJaxb2Classes() {
    this.group = Jaxb2Plugin.TASK_GROUP
  }

  @SuppressWarnings("GroovyUnusedDeclaration")
  @TaskAction
  antXjc() {
    ant.taskdef(
        name: 'xjc',
        classname: project.extensions.jaxb2.taskName,
        classpath: project.configurations.jaxb2.asPath)

    println("====GenerateJaxb2Classes====")
    println("singleConfig.name=${theConfig.name}")
    println("singleConfig.schema=${theConfig.schema}")
    println("============================")

    // Transform package to directory location to specify depends/produces when multiple schema output to same generatedSourcesDir
    // Changing one schema will only cause recompilation/generation of that schema
    def generatedSourcesDirPackage = new File(generatedSourcesDirectory.get().asFile,
            basePackage.get().replace(".", "/"))

    def bindingsDir = theConfig.bindingsDir
    def includedBindingFiles = bindingFileIncludes(theConfig)

    def arguments = [
            destdir  : generatedSourcesDirectory.get().asFile,
            package  : basePackage.get(),
            schema   : schemaFile.get().asFile,
            encoding : encoding.get(),
            extension: extension.get(),
            header   : theConfig.header,
    ]

    if (catalogFile.isPresent()) {
      arguments.catalog = catalogFile.get().asFile
    }

    // the depends and produces is compared using the time-stamp of the schema file and the destination package folder
    ant.xjc(arguments) {
      depends(file: schemaFile.get().asFile)
      produces(dir: generatedSourcesDirPackage, includes: "**/*.java")
      arg(line: additionalArgs.get())

      if (catalogFile.isPresent()) {
        depends(file: catalogFile.get().asFile)
      }

      if (bindingsDir?.trim()) {
        binding(dir: project.file(bindingsDir), includes: includedBindingFiles)
      }
    }
  }

  private static String bindingFileIncludes(XjcTaskConfig config) {
    def files = config.includedBindingFiles

    if (!files?.trim()) {
      LOG.info("No binding file includes defined, falling back to '**/*.xjb' pattern.")
      files = '**/*.xjb'
    }

    LOG.info("Binding files: {}", files)
    return files
  }
}
