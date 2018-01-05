package org.freeplane.plugin.collaboration.client.event.children;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.plugin.collaboration.client.TestData;
import org.freeplane.plugin.collaboration.client.event.TestObjects;
import org.freeplane.plugin.collaboration.client.event.UpdatesEventCaptor;
import org.freeplane.plugin.collaboration.client.event.batch.ModifiableUpdateHeaderExtension;
import org.freeplane.plugin.collaboration.client.event.batch.UpdateBlockCompleted;
import org.freeplane.plugin.collaboration.client.event.batch.Updates;
import org.freeplane.plugin.collaboration.client.event.children.SpecialNodeTypeSet.SpecialNodeType;
import org.freeplane.plugin.collaboration.client.event.content.ContentUpdateGenerators;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChildrenUpdateGeneratorSpec {
	
	private static final int TIMEOUT = 100;

	private static final int DELAY_MILLIS = 10;
	

	
	@Mock
	private StructureUpdateEventFactory structuralEventFactory;

	@Mock
	private ContentUpdateGenerators contentUpdateGenerators;
	private ModifiableUpdateHeaderExtension header = ModifiableUpdateHeaderExtension.create().setMapId("mapId").setMapRevision(0);

	private UpdatesEventCaptor consumer;

	private ChildrenUpdateGenerator uut;

	final private TestObjects testObjects = new TestObjects();
	final private MapModel map = testObjects.map;
	final private NodeModel parent = testObjects.parent;
	final private NodeModel child = testObjects.child;
	final private ChildrenUpdated childrenUpdated = mock(ChildrenUpdated.class);
	
	final private NodeModel parent2 = testObjects.parent2;
	final private NodeModel child2 = testObjects.child2;
	final private ChildrenUpdated childrenUpdated2 = mock(ChildrenUpdated.class);

	@BeforeClass
	static public void setupClass() throws InterruptedException, InvocationTargetException {
		AwtThreadStarter.await();
	}


	@Before
	public void setup() {
		createTestedInstance(1);
	}

	private void createTestedInstance(final int expectedEventCount) {
		consumer = new UpdatesEventCaptor(expectedEventCount);
		Updates updates = new Updates(consumer, DELAY_MILLIS, header);
		uut = new ChildrenUpdateGenerator(updates, structuralEventFactory, contentUpdateGenerators);
	}
	
	@Test
	public void generatesEventOnNodeInsertion() throws Exception {
		
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		uut.onNodeInserted(parent, child);
		
		final UpdateBlockCompleted event = consumer.getEvent();
		UpdateBlockCompleted expected = UpdateBlockCompleted.builder()
				.mapId(header.mapId()).mapRevision(1)
				.addUpdateBlock(childrenUpdated).build();
		
		assertThat(event).isEqualTo(expected);
		assertThat(header.mapRevision()).isEqualTo(1);
	}

	@Test
	public void generatesOneUpdateEventPerParent() throws Exception {
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		
		uut.onNodeInserted(parent, child);
		uut.onNodeInserted(parent, child);
		
		Thread.sleep(TIMEOUT);
		final UpdateBlockCompleted event = consumer.getEvent();
		assertThat(event.updateBlock()).containsExactly(childrenUpdated);
	}
	
	@Test
	public void generatesEventOnNodeInsertionAfterDelay() throws Exception {
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);

		uut.onNodeInserted(parent, child);

		verifyZeroInteractions(structuralEventFactory);
		consumer.getEvent();
	}

	@Test
	public void generatesMultipleEventsOnNodeInsertionToDifferentParents() throws Exception {
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);

		when(structuralEventFactory.createChildrenUpdatedEvent(parent2)).thenReturn(childrenUpdated2);

		uut.onNodeInserted(parent, child);
		uut.onNodeInserted(parent2, child2);
		final UpdateBlockCompleted event = consumer.getEvent();
		assertThat(event.updateBlock()).containsExactly(childrenUpdated, childrenUpdated2);
	}


	@Test
	public void generatesMultipleBatchesOnNodeInsertionToDifferentParentsWithPause() throws Exception {
		createTestedInstance(2);
		
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent2)).thenReturn(childrenUpdated2);

		uut.onNodeInserted(parent, child);
		Thread.sleep(TIMEOUT);
		uut.onNodeInserted(parent2, child2);
		final List<UpdateBlockCompleted> events = consumer.getEvents();
		assertThat(events).hasSize(2);
		assertThat(events.get(0).updateBlock()).containsExactly(childrenUpdated);
		assertThat(events.get(1).updateBlock()).containsExactly(childrenUpdated2);
	}


	@Test
	public void generatesSpecialNodeTypeEventOnNodeInsertion() throws Exception {
		child.addExtension(SummaryNodeFlag.SUMMARY);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent2)).thenReturn(childrenUpdated2);

		parent.insert(child);
		uut.onNodeInserted(parent, child);
		parent2.insert(child2);
		uut.onNodeInserted(parent2, child2);
		
		final UpdateBlockCompleted event = consumer.getEvent();
		final SpecialNodeTypeSet specialNodeTypeUpdated = SpecialNodeTypeSet.builder()
				.nodeId(TestData.CHILD_NODE_ID).content(SpecialNodeType.SUMMARY_END).build();
		assertThat(event.updateBlock()).containsExactly(childrenUpdated, specialNodeTypeUpdated,
			childrenUpdated2);
	}

	@Test
	public void generatesEventsForInsertedChildOnNodeInsertion() throws Exception {
		child.insert(child2);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		when(structuralEventFactory.createChildrenUpdatedEvent(child)).thenReturn(childrenUpdated2);

		parent.insert(child);
		uut.onNodeInserted(parent, child);
		
		final UpdateBlockCompleted event = consumer.getEvent();
		UpdateBlockCompleted expected = UpdateBlockCompleted.builder()
				.mapId(header.mapId()).mapRevision(1)
				.addUpdateBlock(childrenUpdated,
					childrenUpdated2).build();
		
		assertThat(event).isEqualTo(expected);
		assertThat(header.mapRevision()).isEqualTo(1);
	}
	
	@Test
	public void generatesContentEventsForInsertedChildOnNodeInsertion() throws Exception {
		child.insert(child2);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		when(structuralEventFactory.createChildrenUpdatedEvent(child)).thenReturn(childrenUpdated2);
		parent.insert(child);

		uut.onNodeInserted(parent, child);
		consumer.getEvent();
		
		verify(contentUpdateGenerators).onNewNode(child);
	}
	
	@Test
	public void generatesSpecialTypeEventsForInsertedChildOnNodeInsertion() throws Exception {
		child.insert(child2);
		child2.addExtension(SummaryNodeFlag.SUMMARY);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		when(structuralEventFactory.createChildrenUpdatedEvent(child)).thenReturn(childrenUpdated2);

		parent.insert(child);
		uut.onNodeInserted(parent, child);
		
		final UpdateBlockCompleted event = consumer.getEvent();
		
		final SpecialNodeTypeSet specialNodeTypeUpdated = SpecialNodeTypeSet.builder()
				.nodeId(TestData.CHILD_NODE_ID2).content(SpecialNodeType.SUMMARY_END).build();
		assertThat(event.updateBlock()).containsExactly(childrenUpdated,
			childrenUpdated2, specialNodeTypeUpdated);

	
	}

	@Test
	public void generatesEventOnNewMap() throws Exception {
		parent.insert(child);
		when(map.getRootNode()).thenReturn(parent);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		uut.onNewMap(map);
		final UpdateBlockCompleted event = consumer.getEvent();
		final ImmutableRootNodeIdUpdated rootNodeSet = RootNodeIdUpdated.builder().nodeId(TestData.PARENT_NODE_ID).build();
		UpdateBlockCompleted expected = UpdateBlockCompleted.builder()
				.mapId(header.mapId()).mapRevision(1)
				.addUpdateBlock(rootNodeSet, childrenUpdated).build();
		
		assertThat(event).isEqualTo(expected);
	}


	@Test
	public void generatesContentEventsOnNewMap() throws Exception {
		parent.insert(child);
		when(map.getRootNode()).thenReturn(parent);
		when(structuralEventFactory.createChildrenUpdatedEvent(parent)).thenReturn(childrenUpdated);
		uut.onNewMap(map);
		consumer.getEvent();
		
		verify(contentUpdateGenerators).onNewMap(map);
		verify(contentUpdateGenerators).onNewNode(parent);
		verify(contentUpdateGenerators).onNewNode(child);
	}
}
