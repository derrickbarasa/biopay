// The backend only stores a whole-number `age`, not a real date of birth, so forms
// collect a date of birth for a nicer picker/typing UX and convert to/from age here.
// Editing an existing record approximates its unknown birth date as Jan 1 of the
// birth year -- since that's the earliest possible day in the year, recomputing the
// age from it (as long as "today" isn't itself Jan 1) always round-trips back to the
// original recorded age when the date is left untouched.
export function ageToDateOfBirth(age: number | null | undefined): Date | null {
  if (age === null || age === undefined || Number.isNaN(age)) return null
  return new Date(new Date().getFullYear() - age, 0, 1)
}

export function dateOfBirthToAge(dob: Date | null | undefined): number | null {
  if (!dob) return null
  const today = new Date()
  let age = today.getFullYear() - dob.getFullYear()
  const hadBirthdayThisYear = today.getMonth() > dob.getMonth()
    || (today.getMonth() === dob.getMonth() && today.getDate() >= dob.getDate())
  if (!hadBirthdayThisYear) age--
  return age
}
