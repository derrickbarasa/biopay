// Mirrors the mobile field app's relationship list and gender inference
// (mobile/agent .../households/RelationshipGender.java + alternate_relationship_options).
export const ALTERNATE_RELATIONSHIP_OPTIONS = [
  'Wife', 'Husband', 'Son', 'Daughter', 'Brother', 'Sister', 'Mother', 'Father',
  'Grandmother', 'Grandfather', 'Grandson', 'Granddaughter', 'Aunt', 'Uncle',
  'Niece', 'Nephew', 'Cousin', 'In-law', 'Ward', 'Other',
] as const

const GENDER_BY_RELATIONSHIP: Record<string, 'M' | 'F'> = {
  husband: 'M', son: 'M', brother: 'M', father: 'M', grandfather: 'M', grandson: 'M', uncle: 'M', nephew: 'M',
  wife: 'F', daughter: 'F', sister: 'F', mother: 'F', grandmother: 'F', granddaughter: 'F', aunt: 'F', niece: 'F',
}

// Relationships with no unambiguous gender (cousin, in-law, ward, other) return
// undefined so the gender field stays editable instead of being forced.
export function inferGenderFromRelationship(relationship?: string): 'M' | 'F' | undefined {
  if (!relationship) return undefined
  return GENDER_BY_RELATIONSHIP[relationship.trim().toLowerCase()]
}
