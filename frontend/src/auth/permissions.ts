import type { MembershipRole } from "../types/api";

export type Permission =
  | "viewMembers"
  | "inviteMembers"
  | "manageCompanies"
  | "uploadInvoices"
  | "revalidateInvoices"
  | "deleteInvoices"
  | "manageDataLifecycle";

const permissions: Record<MembershipRole, Permission[]> = {
  OWNER: [
    "viewMembers",
    "inviteMembers",
    "manageCompanies",
    "uploadInvoices",
    "revalidateInvoices",
    "deleteInvoices",
    "manageDataLifecycle"
  ],
  ACCOUNTANT: ["viewMembers", "inviteMembers", "manageCompanies", "uploadInvoices", "revalidateInvoices", "deleteInvoices"],
  EMPLOYEE: ["uploadInvoices", "revalidateInvoices"],
  CLIENT: []
};

export function hasPermission(role: MembershipRole | undefined, permission: Permission) {
  return role ? permissions[role].includes(permission) : false;
}
