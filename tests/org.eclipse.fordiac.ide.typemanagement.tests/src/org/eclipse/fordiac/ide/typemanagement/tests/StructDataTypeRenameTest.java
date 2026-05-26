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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.fordiac.ide.model.data.StructuredType;
import org.eclipse.fordiac.ide.model.libraryElement.Application;
import org.eclipse.fordiac.ide.model.libraryElement.AutomationSystem;
import org.eclipse.fordiac.ide.model.libraryElement.ConfigurableFB;
import org.eclipse.fordiac.ide.model.libraryElement.FBType;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.fordiac.ide.test.model.FordiacProjectLoader;
import org.eclipse.ltk.core.refactoring.Change;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

class StructDataTypeRenameTest {

	private static final String BUNDLE_NAME = "org.eclipse.fordiac.ide.typemanagement.tests"; //$NON-NLS-1$
	private static final String PROJECT_PATH = "data/StructRenameTest"; //$NON-NLS-1$
	private static final String SYSTEM_NAME = "StructRenameTest"; //$NON-NLS-1$
	private static final String APP_NAME = "StructRenameApp"; //$NON-NLS-1$
	private static final String DEMUX_NAME = "Demux"; //$NON-NLS-1$
	private static final String INNER_STRUCT_FILE = "Type Library/mypackage/InnerStruct.dtp"; //$NON-NLS-1$
	private static final String RENAMED_FILE = "InnerStructRenamed.dtp"; //$NON-NLS-1$
	private static final String OLD_NAME = "InnerStruct"; //$NON-NLS-1$
	private static final String NEW_NAME = "InnerStructRenamed"; //$NON-NLS-1$

	private static FordiacProjectLoader loader;
	private static TypeLibrary typeLibrary;

	@BeforeAll
	static void loadFixture() throws Exception {
		final Bundle bundle = Platform.getBundle(BUNDLE_NAME);
		loader = new FordiacProjectLoader(bundle, new Path(PROJECT_PATH));
		typeLibrary = TypeLibraryManager.INSTANCE.getTypeLibrary(loader.getEclipseProject());
	}

	@Test
	void renameInnerStruct_cascadesAcrossStructMemberInterfaceAndDemuxConfiguration() throws Exception {
		final IFile innerStructFile = loader.getEclipseProject().getFile(INNER_STRUCT_FILE);

		assertOuterStructMember(OLD_NAME);
		assertStructProducerOutput(OLD_NAME);
		assertDemuxConfiguration(OLD_NAME);

		final Change undo = RefactoringTestSupport.performRename(innerStructFile, RENAMED_FILE);

		assertOuterStructMember(NEW_NAME);
		assertStructProducerOutput(NEW_NAME);
		assertDemuxConfiguration(NEW_NAME);

		final Change redo = RefactoringTestSupport.performChange(undo);

		assertOuterStructMember(OLD_NAME);
		assertStructProducerOutput(OLD_NAME);
		assertDemuxConfiguration(OLD_NAME);

		RefactoringTestSupport.performChange(redo);

		assertOuterStructMember(NEW_NAME);
		assertStructProducerOutput(NEW_NAME);
		assertDemuxConfiguration(NEW_NAME);
	}

	private static void assertOuterStructMember(final String expectedTypeName) {
		final StructuredType outer = typeLibrary.getDataTypeLibrary().getStructuredType("mypackage::OuterStruct"); //$NON-NLS-1$
		final VarDeclaration inner = outer.getMemberVariables().get(0);
		assertEquals(expectedTypeName, inner.getFullTypeName());
	}

	private static void assertStructProducerOutput(final String expectedTypeName) {
		final FBType producer = typeLibrary.getFBTypeEntry("mypackage::StructProducer").getType(); //$NON-NLS-1$
		final VarDeclaration out = producer.getInterfaceList().getOutputVars().get(0);
		assertEquals(expectedTypeName, out.getFullTypeName());
	}

	private static void assertDemuxConfiguration(final String expectedSimpleName) {
		final AutomationSystem system = loader.getAutomationSystem(SYSTEM_NAME);
		final Application app = system.getApplicationNamed(APP_NAME);
		final ConfigurableFB demux = (ConfigurableFB) app.getFBNetwork().getElementNamed(DEMUX_NAME);
		assertEquals(expectedSimpleName, demux.getDataType().getName());
	}
}
