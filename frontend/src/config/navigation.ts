import type { Role } from '@/types/auth';
import type { IconName } from '@/components/ui/Icon';
import { getRoleRoute } from '@/lib/roleRoute';

export interface NavItem {
  id: string;
  label: string;
  icon: IconName;
  href?: string; // If undefined, it acts as a disabled/placeholder item
}

export interface NavSection {
  id: string;
  label?: string;
  items: NavItem[];
}

/**
 * Returns the visible navigation, grouped into sections, for a specific role.
 * Only implemented features have a valid href.
 */
export function getNavigationForRole(role: Role): NavSection[] {
  const dashboard: NavItem = {
    id: 'dashboard',
    label: 'Dashboard',
    icon: 'home',
    href: getRoleRoute(role),
  };

  switch (role) {
    case 'CLUB_MANAGER':
      return [
        { id: 'overview', items: [dashboard] },
        {
          id: 'operations',
          label: 'Operations',
          items: [
            { id: 'admissions', label: 'Admissions', icon: 'clipboard', href: '/manager/admissions' },
            { id: 'horses', label: 'Horses', icon: 'horse', href: '/manager/horses' },
            { id: 'stable', label: 'Stable', icon: 'building' },
          ],
        },
        {
          id: 'administration',
          label: 'Administration',
          items: [
            { id: 'staff', label: 'Staff', icon: 'users', href: '/manager/staff' },
            { id: 'access-control', label: 'Access Control', icon: 'shield', href: '/manager/access-control' },
            { id: 'audit-log', label: 'Audit Log', icon: 'file-text', href: '/manager/audit-log' },
            { id: 'reports', label: 'Reports', icon: 'trending-up' },
          ],
        },
      ];
    case 'HEAD_TRAINER':
      return [
        { id: 'overview', items: [dashboard] },
        {
          id: 'training',
          label: 'Training',
          items: [
            { id: 'training', label: 'Training', icon: 'target', href: '/trainer/courses' },
            { id: 'plans', label: 'Plans', icon: 'layers', href: '/trainer/plans' },
            { id: 'schedule', label: 'Schedule', icon: 'calendar', href: '/trainer/schedule' },
          ],
        },
        {
          id: 'operations',
          label: 'Operations',
          items: [
            { id: 'admissions', label: 'Admissions', icon: 'clipboard', href: '/trainer/admissions' },
            { id: 'stable', label: 'Stable', icon: 'building', href: '/trainer/stable' },
            { id: 'horses', label: 'Horses', icon: 'horse', href: '/trainer/horses' },
            { id: 'racing', label: 'Racing', icon: 'flag', href: '/trainer/racing' },
          ],
        },
      ];
    case 'VETERINARIAN':
      return [
        { id: 'overview', items: [dashboard] },
        {
          id: 'clinical',
          label: 'Clinical',
          items: [
            { id: 'admissions', label: 'Admissions', icon: 'clipboard', href: '/veterinarian/admissions' },
            { id: 'health', label: 'Health', icon: 'heart-pulse' },
            { id: 'preventive-care', label: 'Preventive Care', icon: 'pill' },
          ],
        },
      ];
    case 'GROOM':
      return [
        { id: 'overview', items: [dashboard] },
        {
          id: 'care',
          label: 'Care',
          items: [
            { id: 'care-tasks', label: 'Daily Tasks', icon: 'list', href: '/groom/tasks' },
            { id: 'incidents', label: 'Incidents', icon: 'alert-triangle', href: '/groom/incidents' },
            { id: 'admissions', label: 'Admissions', icon: 'clipboard', href: '/groom/admissions' },
            { id: 'stable', label: 'Stable', icon: 'building', href: '/groom/stable' },
          ],
        },
      ];
    case 'HORSE_OWNER':
      return [
        { id: 'overview', items: [dashboard] },
        {
          id: 'my-stable',
          label: 'My stable',
          items: [
            { id: 'my-horses', label: 'My Horses', icon: 'horse' },
            { id: 'admissions', label: 'Admissions', icon: 'clipboard', href: '/owner/admissions' },
            { id: 'racing', label: 'Racing', icon: 'flag' },
          ],
        },
      ];
    default:
      return [];
  }
}

/** The dashboard item matches its route exactly; every other item matches its subtree. */
export function isNavItemActive(pathname: string, item: NavItem, role: Role): boolean {
  if (!item.href) return false;
  if (item.href === getRoleRoute(role)) return pathname === item.href;
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
}
