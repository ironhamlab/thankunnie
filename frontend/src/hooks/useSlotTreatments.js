import { useEffect } from 'react';
import { useTreatmentStore } from '@/store/useTreatmentStore';

const toMenu = (treatment) => ({
  id: `treatment-${treatment.treatmentId}`,
  treatmentId: treatment.treatmentId,
  name: treatment.name,
  duration: treatment.durationMinutes,
  original: treatment.regularPrice,
  active: treatment.status === 'ACTIVE',
});

export default function useSlotTreatments() {
  const treatments = useTreatmentStore((state) => state.activeTreatments);
  const status = useTreatmentStore((state) => state.selectionStatus);
  const error = useTreatmentStore((state) => state.selectionError);
  const load = useTreatmentStore((state) => state.loadActiveTreatments);

  useEffect(() => {
    load().catch(() => {});
  }, [load]);

  const refreshMenus = async () => (await load()).map(toMenu);
  return { menus: treatments.map(toMenu), status, error, refreshMenus };
}
