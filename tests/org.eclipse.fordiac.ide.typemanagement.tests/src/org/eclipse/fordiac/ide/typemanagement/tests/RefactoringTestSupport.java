/*******************************************************************************
 * Copyright (c) 2026
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Dimitrios Kalligaridis - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.typemanagement.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.fordiac.ide.library.LibraryManager;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceDescriptor;
import org.osgi.framework.Bundle;

public final class RefactoringTestSupport {

	/**
	 * Copy a bundle-relative project template into a fresh, writable project in the
	 * test workspace.
	 *
	 * The fixture data shipped with the test bundle is read-only template data;
	 * refactorings rename and rewrite type files, so each test must operate on its
	 * own writable copy instead of mutating the committed fixture in place.
	 */
	public static IProject importProjectIntoWorkspace(final String projectName, final Bundle bundle,
			final IPath bundleRelativePath) throws CoreException, IOException {
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IProject project = root.getProject(projectName);
		deleteProject(project);

		final java.nio.file.Path source = resolveBundleDirectory(bundle, bundleRelativePath);
		final java.nio.file.Path destination = root.getLocation().append(projectName).toFile().toPath();
		deleteRecursively(destination);
		copyRecursively(source, destination);

		final IPath descriptionPath = Path.fromOSString(destination.resolve(".project").toString()); //$NON-NLS-1$
		final IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(descriptionPath);
		// destination is the default location (workspaceRoot/projectName), so no
		// explicit location needs to be set on the description
		project.create(description, new NullProgressMonitor());
		project.open(new NullProgressMonitor());
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		return project;
	}

	/**
	 * Link core 3.0.0 and convert 3.0.0 from the source tree into the workspace
	 * project so types like STRUCT_DEMUX resolve at test runtime, instead of
	 * shipping a copy in the fixture.
	 *
	 * Tycho runs the test JVM with the test bundle directory as the working
	 * directory, so the two parent segments climb from the bundle root to the
	 * repository root, where data/typelibrary holds the standard libraries.
	 */
	public static void linkStandardLibraries(final IProject project) throws CoreException {
		ensureLibraryFolders(project);
		final java.nio.file.Path standardLibraries = java.nio.file.Path
				.of(System.getProperty("user.dir"), "..", "..", "data", "typelibrary") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				.toAbsolutePath().normalize();
		LibraryManager.INSTANCE.importLibrary(project, standardLibraries.resolve("core-3.0.0").toUri(), true, false); //$NON-NLS-1$
		LibraryManager.INSTANCE.importLibrary(project, standardLibraries.resolve("convert-3.0.0").toUri(), true, //$NON-NLS-1$
				false);
		// Drop the cached type library so the next lookup re-parses .sys and .fbt
		// files with the freshly linked libraries in scope; a plain refresh() only
		// reconciles added or deleted files, it does not re-parse already loaded
		// entries that referenced types from the not-yet-linked libraries.
		TypeLibraryManager.INSTANCE.removeProject(project);
		TypeLibraryManager.INSTANCE.getTypeLibrary(project).refresh();
	}

	private static void ensureLibraryFolders(final IProject project) throws CoreException {
		final NullProgressMonitor monitor = new NullProgressMonitor();
		final org.eclipse.core.resources.IFolder standardLibs = project.getFolder("Standard Libraries"); //$NON-NLS-1$
		if (!standardLibs.exists()) {
			standardLibs.create(IResource.VIRTUAL | IResource.FORCE, true, monitor);
		}
		final org.eclipse.core.resources.IFolder externalLibs = project.getFolder("External Libraries"); //$NON-NLS-1$
		if (!externalLibs.exists()) {
			externalLibs.create(IResource.VIRTUAL | IResource.FORCE, true, monitor);
		}
	}

	/** Remove a project from the workspace and drop its cached type library. */
	public static void deleteProject(final IProject project) throws CoreException {
		if (project.exists()) {
			TypeLibraryManager.INSTANCE.removeProject(project);
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	public static Change performRename(final IFile file, final String newName) throws CoreException {
		final RenameResourceDescriptor descriptor = new RenameResourceDescriptor();
		descriptor.setResourcePath(file.getFullPath());
		descriptor.setNewName(newName);

		final RefactoringStatus status = new RefactoringStatus();
		final Refactoring refactoring = descriptor.createRefactoring(status);

		final CreateChangeOperation create = new CreateChangeOperation(
				new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS),
				RefactoringStatus.FATAL);
		final PerformChangeOperation perform = new PerformChangeOperation(create);
		perform.setUndoManager(RefactoringCore.getUndoManager(), refactoring.getName());
		ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());
		return perform.getUndoChange();
	}

	public static Change performChange(final Change change) throws CoreException {
		final PerformChangeOperation perform = new PerformChangeOperation(change);
		ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());
		return perform.getUndoChange();
	}

	private static java.nio.file.Path resolveBundleDirectory(final Bundle bundle, final IPath bundleRelativePath)
			throws IOException {
		final var url = FileLocator.toFileURL(FileLocator.find(bundle, bundleRelativePath));
		return Paths.get(url.getPath());
	}

	private static void copyRecursively(final java.nio.file.Path source, final java.nio.file.Path destination)
			throws IOException {
		try (var paths = Files.walk(source)) {
			for (final var path : (Iterable<java.nio.file.Path>) paths::iterator) {
				final java.nio.file.Path target = destination.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteRecursively(final java.nio.file.Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	private RefactoringTestSupport() {
		throw new UnsupportedOperationException();
	}
}
