// Server tarafında okunur (page/action); client'a prop olarak iner.
export const flags = {
  cabWorkflow: process.env.FEATURE_CAB_WORKFLOW !== 'false', // default: açık
};