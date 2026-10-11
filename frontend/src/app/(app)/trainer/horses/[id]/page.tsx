import { redirect } from 'next/navigation';

/** Old link format: the Horses page now takes the horse as ?horseId=, so keep old bookmarks working. */
export default async function LegacyHorsePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  redirect(`/trainer/horses?horseId=${encodeURIComponent(id)}`);
}
