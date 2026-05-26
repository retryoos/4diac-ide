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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceDescriptor;

public final class RefactoringTestSupport {

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

	private RefactoringTestSupport() {
		throw new UnsupportedOperationException();
	}
}
