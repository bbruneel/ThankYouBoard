/** Episode entity returned by GET /episodes/ and GET /episodes/{id}. */
export type Episode = {
  id: number;
  title: string;
  summary: string | null;
};
