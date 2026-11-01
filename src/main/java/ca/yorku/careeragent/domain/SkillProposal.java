package ca.yorku.careeragent.domain;

/** A proposed skill and a verbatim supporting excerpt, awaiting candidate review. */
public record SkillProposal(String skill, String evidence) { }
