export const titleCase = (id: string): string =>
  id
    .split(/[_\s]+/u)
    .filter((word) => word !== "")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
