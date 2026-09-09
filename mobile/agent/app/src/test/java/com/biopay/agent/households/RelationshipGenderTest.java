package com.biopay.agent.households;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RelationshipGenderTest {
    @Test public void infersUnambiguousMaleRelationships() {
        assertEquals("Male", RelationshipGender.infer("Son"));
        assertEquals("Male", RelationshipGender.infer("Husband"));
        assertEquals("Male", RelationshipGender.infer("Nephew"));
    }

    @Test public void infersUnambiguousFemaleRelationships() {
        assertEquals("Female", RelationshipGender.infer("Daughter"));
        assertEquals("Female", RelationshipGender.infer("Wife"));
        assertEquals("Female", RelationshipGender.infer("Niece"));
    }

    @Test public void leavesAmbiguousRelationshipsForTheOfficer() {
        assertNull(RelationshipGender.infer("Cousin"));
        assertNull(RelationshipGender.infer("In-law"));
        assertNull(RelationshipGender.infer("Other"));
    }
}
