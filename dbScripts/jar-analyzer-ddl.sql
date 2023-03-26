CREATE TABLE maven_effective_pom (
project_remote_url VARCHAR(300),
commit_id VARCHAR(200),
effective_pom mediumtext
);

create index idx_url_commit_id ON maven_effective_pom (project_remote_url, commit_id);

CREATE TABLE jar (
id INT NOT NULL AUTO_INCREMENT,
group_id VARCHAR(100),
artifact_id VARCHAR(100) NOT NULL,
version VARCHAR(100) NOT NULL,
PRIMARY KEY (id)
);

create index idx_jar_g_a_v on jar (group_id, artifact_id, version);
create index idx_jar_a_v on jar (artifact_id, version);

CREATE TABLE class (
id INT NOT NULL AUTO_INCREMENT,
name VARCHAR(255) NOT NULL,
q_name VARCHAR(355) NOT NULL,
package_name VARCHAR(120),
is_abstract BOOL,
is_interface BOOL,
is_enum BOOL,
is_public BOOL,
is_private BOOL,
is_protected BOOL,
is_inner_class BOOL,
is_anonymous_inner_class BOOL,
type_descriptor VARCHAR(1500),
signature VARCHAR(6000),
jar_id INT NOT NULL,
primary key (id),
foreign key (jar_id) REFERENCES jar (id)
);

create index idx_jar_id_qname on class (jar_id, q_name);
create index idx_jar_id_packagename on class (jar_id, package_name);
CREATE fulltext index idx_name on class(name);

CREATE TABLE super_class_relation (
child_class_id INT NOT NULL,
parent_class_q_name VARCHAR(1500) NOT NULL,
type VARCHAR(100) NOT NULL,
precedence INT NOT NULL,
foreign key (child_class_id) REFERENCES class(id)
);

CREATE TABLE method (
id INT NOT NULL AUTO_INCREMENT,
class_id INT NOT NULL,
name VARCHAR(1000) NOT NULL,
is_abstract BOOL,
is_constructor BOOL,
is_static BOOL,
is_public BOOL,
is_private BOOL,
is_protected BOOL,
is_synchronized BOOL,
is_final BOOL,
is_varargs BOOL,
is_bridge_method BOOL,
signature VARCHAR(4000),
internal_class_constructor_prefix VARCHAR(1000),
return_type_descriptor VARCHAR(1000),
PRIMARY KEY (id),
foreign key (class_id) REFERENCES class (id)
);

CREATE TABLE argument_type_descriptor (
precedence_order INT NOT NULL,
argument_type_descriptor VARCHAR(1000),
method_id INT NOT NULL,
foreign key (method_id) references method(id)
);

CREATE TABLE thrown_class_name (
precedence_order INT NOT NULL,
thrown_class_name VARCHAR(1000),
method_id INT NOT NULL,
foreign key (method_id) references method(id)
);

CREATE TABLE field (
id INT NOT NULL AUTO_INCREMENT,
class_id INT NOT NULL,
name VARCHAR(1000) NOT NULL,
is_public BOOL,
is_private BOOL,
is_protected BOOL,
is_static BOOL,
type_descriptor VARCHAR(1000),
signature VARCHAR(2000),
PRIMARY KEY (id),
foreign key (class_id) REFERENCES class (id)
);

CREATE TABLE inner_class_name (
parent_class_id INT NOT NULL,
inner_class_q_name VARCHAR(1000) NOT NULL,
foreign key (parent_class_id) REFERENCES class (id)
);
